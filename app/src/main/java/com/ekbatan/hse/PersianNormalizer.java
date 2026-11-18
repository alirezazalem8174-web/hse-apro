package com.ekbatan.hse;

import java.util.Locale;

/** Conservative Persian/Arabic text normalization for local HSE search. */
public final class PersianNormalizer {
    private PersianNormalizer() {}
    public static String normalize(String s) {
        if (s == null) return "";
        String x = s.trim();
        x = x.replace('\u064A','\u06CC').replace('\u0649','\u06CC').replace('\u0643','\u06A9');
        x = x.replace('\u0640',' '); // tatweel
        x = x.replace('\u200C',' ').replace('\u200D',' '); // ZWNJ/ZWJ -> searchable boundary
        x = x.replace('\uFEFF',' ');
        // Arabic/Persian digits -> ASCII digits
        x=x.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9');
        x=x.replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');
        // Remove Arabic diacritics and normalize common punctuation to spaces.
        x=x.replaceAll("[\\u064B-\\u065F\\u0670]", "");
        x=x.replaceAll("[،؛,:،.؛!?؟()\\[\\]{}<>/\\\\|_+=*#@%$^~`\"']", " ");
        return x.replaceAll("[\\t\\r\\n ]+"," ").trim().toLowerCase(Locale.ROOT);
    }
}
