package com.billing.gst;

import java.util.regex.Pattern;

/** Checks the shape and the check character of a GSTIN. */
public final class GstinValidator {

    private static final Pattern FORMAT = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private GstinValidator() {
    }

    public static String normalize(String gstin) {
        return gstin == null ? null : gstin.trim().toUpperCase();
    }

    /** True when the GSTIN has the right format, a real state code and a correct check character. */
    public static boolean isValid(String gstin) {
        String g = normalize(gstin);
        if (g == null || !FORMAT.matcher(g).matches()) {
            return false;
        }
        if (!GstStates.isValid(g.substring(0, 2))) {
            return false;
        }
        return checkCharacter(g.substring(0, 14)) == g.charAt(14);
    }

    public static String stateCodeOf(String gstin) {
        String g = normalize(gstin);
        return (g == null || g.length() < 2) ? null : g.substring(0, 2);
    }

    static char checkCharacter(String first14) {
        int sum = 0;
        for (int i = 0; i < first14.length(); i++) {
            int value = CHARS.indexOf(first14.charAt(i));
            int product = value * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return CHARS.charAt((36 - sum % 36) % 36);
    }
}
