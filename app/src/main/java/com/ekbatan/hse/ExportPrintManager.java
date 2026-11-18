package com.ekbatan.hse;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PageRange;
import android.print.PrintManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.TextPaint;
import android.text.StaticLayout;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

/** Offline copy/print helpers. No network or paid service is used. */
public final class ExportPrintManager {
    private ExportPrintManager() {}

    public static void copy(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) throw new IllegalStateException("مدیریت کلیپ‌بورد در دسترس نیست");
        String value = text == null ? "" : text;
        cm.setPrimaryClip(ClipData.newPlainText(label == null ? "HSE AI PRO" : label, value));
    }

    public static void print(Context c, String jobName, String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("داده‌ای برای چاپ وجود ندارد");
        PrintManager pm = (PrintManager)c.getSystemService(Context.PRINT_SERVICE);
        if (pm == null) throw new IllegalStateException("سرویس چاپ در دسترس نیست");
        pm.print(jobName == null ? "HSE AI PRO" : jobName, new TextPrintAdapter(c, value),
                new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
    }

    private static final class TextPrintAdapter extends PrintDocumentAdapter {
        private final Context context;
        private final String text;
        private PrintAttributes attributes;
        private StaticLayout layout;
        private int pageHeight;
        private int pageWidth;

        TextPrintAdapter(Context context, String text) { this.context = context; this.text = text; }

        @Override public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                       android.os.CancellationSignal cancellationSignal,
                                       LayoutResultCallback callback, Bundle extras) {
            if (cancellationSignal.isCanceled()) { callback.onLayoutCancelled(); return; }
            attributes = newAttributes;
            android.print.PrintAttributes.Resolution res = newAttributes.getResolution();
            int dpi = res == null ? 300 : Math.max(72, res.getHorizontalDpi());
            pageWidth = Math.max(1, newAttributes.getMediaSize().getWidthMils() * dpi / 1000 - 2 * 90);
            pageHeight = Math.max(1, newAttributes.getMediaSize().getHeightMils() * dpi / 1000 - 2 * 90);
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            paint.setColor(android.graphics.Color.BLACK);
            paint.setTextSize(Math.max(22, dpi * 10 / 72));
            paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, pageWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(true).setLineSpacing(0, 1.15f)
                    .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
                    .build();
            int pages = Math.max(1, (int)Math.ceil(layout.getHeight() / (double)pageHeight));
            PrintDocumentInfo info = new PrintDocumentInfo.Builder("HSE_AI_PRO_Print.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build();
            callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
        }

        @Override public void onWrite(PageRange[] pages, android.os.ParcelFileDescriptor destination,
                                      android.os.CancellationSignal cancellationSignal, WriteResultCallback callback) {
            if (layout == null || attributes == null) { callback.onWriteFailed("چیدمان چاپ آماده نیست"); return; }
            PdfDocument pdf = new PdfDocument();
            try (FileOutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                int totalPages = Math.max(1, (int)Math.ceil(layout.getHeight() / (double)pageHeight));
                java.util.ArrayList<PageRange> writtenRanges = new java.util.ArrayList<>();
                int rangeStart = -1, rangeEnd = -1;
                for (int pageNo = 0; pageNo < totalPages; pageNo++) {
                    if (cancellationSignal.isCanceled()) { callback.onWriteCancelled(); return; }
                    if (!includes(pages, pageNo)) continue;
                    PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageWidth + 180, pageHeight + 180, pageNo + 1).create());
                    Canvas canvas = page.getCanvas();
                    canvas.translate(90, 90 - pageNo * pageHeight);
                    layout.draw(canvas);
                    pdf.finishPage(page);
                    if (rangeStart < 0) rangeStart = pageNo;
                    rangeEnd = pageNo;
                }
                if (rangeStart >= 0) writtenRanges.add(new PageRange(rangeStart, rangeEnd));
                pdf.writeTo(out);
                callback.onWriteFinished(writtenRanges.isEmpty() ? new PageRange[0] : writtenRanges.toArray(new PageRange[0]));
            } catch (Exception e) {
                callback.onWriteFailed(e.getMessage() == null ? "خطا در تولید فایل چاپ" : e.getMessage());
            } finally { pdf.close(); }
        }

        private boolean includes(PageRange[] ranges, int page) {
            if (ranges == null || ranges.length == 0) return true;
            for (PageRange r : ranges) if (r.getStart() <= page && page <= r.getEnd()) return true;
            return false;
        }
    }
}
