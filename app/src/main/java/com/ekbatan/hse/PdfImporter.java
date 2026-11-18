package com.ekbatan.hse;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

/** Safe, transactional PDF importer with size/page limits and duplicate detection. */
public final class PdfImporter {
    private static final long MAX_PDF_BYTES=100L*1024L*1024L;
    private static final int MAX_PAGES=10000;
    private static final int MAX_PAGE_TEXT_CHARS=500_000;
    private static final long MAX_TOTAL_TEXT_CHARS=50_000_000L;
    public static class Result{public int pages,textPages;public String title;Result(int p,int t,String x){pages=p;textPages=t;title=x;}}
    public static Result importPdf(Context c,Uri uri,LibraryDb db,String title) throws Exception {
        if(uri==null)throw new IOException("فایل PDF انتخاب نشده است");
        PDFBoxResourceLoader.init(c);String safe=sanitize(title);File dir=new File(c.getFilesDir(),"library");
        if(Files.isSymbolicLink(dir.toPath()))throw new IOException("پوشه کتابخانه نامعتبر است");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("ساخت پوشه کتابخانه ناموفق بود");
        File out=new File(dir,System.nanoTime()+"_"+safe+".pdf");
        File canonical=out.getCanonicalFile();if(!canonical.getParentFile().equals(dir.getCanonicalFile()))throw new IOException("مسیر PDF ناامن است");
        MessageDigest md=MessageDigest.getInstance("SHA-256");long total=0;
        try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream os=new BufferedOutputStream(new FileOutputStream(out))){
            if(in==null)throw new IOException("فایل PDF قابل خواندن نیست");byte[] b=new byte[32768];int n;
            while((n=in.read(b))!=-1){total+=n;if(total>MAX_PDF_BYTES)throw new IOException("حجم PDF بیش از 100MB است");md.update(b,0,n);os.write(b,0,n);}
        } catch(Exception e){out.delete();throw e;}
        String hash=hex(md.digest());if(db.hasHash(hash)){out.delete();throw new IOException("این PDF قبلاً وارد شده است.");}
        long doc=-1;int pages=0,textPages=0;long totalTextChars=0;
        try(PDDocument pdf=PDDocument.load(out)){
            pages=pdf.getNumberOfPages();if(pages<=0||pages>MAX_PAGES)throw new IOException("تعداد صفحات PDF غیرمجاز است");
            android.database.sqlite.SQLiteDatabase w=db.getWritableDatabase();w.beginTransaction();
            try {
                doc=db.addDocument(safe,"عمومی",safe,out.getAbsolutePath(),hash);PDFTextStripper stripper=new PDFTextStripper();
                for(int p=1;p<=pages;p++){stripper.setStartPage(p);stripper.setEndPage(p);String tx=stripper.getText(pdf);
                    if(tx==null) tx="";
                    if(tx.length()>MAX_PAGE_TEXT_CHARS) tx=tx.substring(0,MAX_PAGE_TEXT_CHARS)+"\n[ادامه متن این صفحه برای کنترل حجم ذخیره‌سازی حذف شد]";
                    totalTextChars+=tx.length();
                    if(totalTextChars>MAX_TOTAL_TEXT_CHARS) throw new IOException("حجم کل متن استخراج‌شده از PDF بیش از حد مجاز است");
                    if(!tx.trim().isEmpty())textPages++;
                    db.addPage(doc,p,tx);}
                w.setTransactionSuccessful();
            } finally { w.endTransaction(); }
        }catch(Exception e){if(doc>0)try{db.deleteDocument(doc);}catch(Exception ignored){}out.delete();throw e;}
        return new Result(pages,textPages,safe);
    }
    private static String sanitize(String title){String s=title==null?"منبع PDF":title.trim();if(s.isEmpty())s="منبع PDF";s=s.replaceAll("[\\r\\n/\\\\:*?\"<>|]","_");if(s.length()>100)s=s.substring(0,100);return s;}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder(b.length*2);for(byte x:b)s.append(String.format("%02x",x));return s.toString();}
}
