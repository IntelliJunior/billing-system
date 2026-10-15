package com.billing.gst;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** "Rupees One Thousand Two Hundred and Fifty Paise Only" using the Indian crore/lakh system. */
public final class AmountInWords {

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private AmountInWords() {
    }

    public static String rupees(BigDecimal amount) {
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        boolean negative = rounded.signum() < 0;
        BigDecimal abs = rounded.abs();
        long rupees = abs.toBigInteger().longValueExact();
        int paise = abs.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();

        StringBuilder sb = new StringBuilder();
        if (negative) sb.append("Minus ");
        sb.append("Rupees ").append(rupees == 0 ? "Zero" : words(rupees));
        if (paise > 0) {
            sb.append(" and ").append(words(paise)).append(" Paise");
        }
        return sb.append(" Only").toString();
    }

    private static String words(long n) {
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000L;
        n %= 10_000_000L;
        long lakh = n / 100_000L;
        n %= 100_000L;
        long thousand = n / 1_000L;
        n %= 1_000L;
        long hundred = n / 100L;
        long rest = n % 100L;

        if (crore > 0) sb.append(words(crore)).append(" Crore ");
        if (lakh > 0) sb.append(twoDigits((int) lakh)).append(" Lakh ");
        if (thousand > 0) sb.append(twoDigits((int) thousand)).append(" Thousand ");
        if (hundred > 0) sb.append(ONES[(int) hundred]).append(" Hundred ");
        if (rest > 0) sb.append(twoDigits((int) rest));
        return sb.toString().trim();
    }

    private static String twoDigits(int n) {
        if (n < 20) return ONES[n];
        return TENS[n / 10] + (n % 10 > 0 ? " " + ONES[n % 10] : "");
    }
}
