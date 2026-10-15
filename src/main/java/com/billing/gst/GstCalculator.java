package com.billing.gst;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * The GST arithmetic in one place.
 *  - Within one state: CGST and SGST, each half of the rate, each rounded to paise.
 *  - Between states: IGST at the full rate.
 */
public final class GstCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO_HUNDRED = BigDecimal.valueOf(200);

    public record LineResult(BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                             BigDecimal tax, BigDecimal total) {
    }

    private GstCalculator() {
    }

    public static LineResult line(int quantity, BigDecimal unitPrice, BigDecimal ratePercent, boolean interState) {
        BigDecimal taxable = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cgst = BigDecimal.ZERO.setScale(2);
        BigDecimal sgst = BigDecimal.ZERO.setScale(2);
        BigDecimal igst = BigDecimal.ZERO.setScale(2);
        if (interState) {
            igst = taxable.multiply(ratePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        } else {
            cgst = taxable.multiply(ratePercent).divide(TWO_HUNDRED, 2, RoundingMode.HALF_UP);
            sgst = cgst;
        }
        BigDecimal tax = cgst.add(sgst).add(igst);
        return new LineResult(taxable, cgst, sgst, igst, tax, taxable.add(tax));
    }

    public static LineResult sum(List<LineResult> lines) {
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        for (LineResult l : lines) {
            taxable = taxable.add(l.taxable());
            cgst = cgst.add(l.cgst());
            sgst = sgst.add(l.sgst());
            igst = igst.add(l.igst());
        }
        BigDecimal tax = cgst.add(sgst).add(igst);
        return new LineResult(taxable.setScale(2, RoundingMode.HALF_UP), cgst.setScale(2, RoundingMode.HALF_UP),
                sgst.setScale(2, RoundingMode.HALF_UP), igst.setScale(2, RoundingMode.HALF_UP),
                tax.setScale(2, RoundingMode.HALF_UP), taxable.add(tax).setScale(2, RoundingMode.HALF_UP));
    }

    /** Goods or services moving to a different state than the supplier's are inter-state. */
    public static boolean isInterState(String supplierStateCode, String placeOfSupplyStateCode) {
        return !supplierStateCode.equals(placeOfSupplyStateCode);
    }

    /** Indian financial year (1 April to 31 March) as "26-27". */
    public static String financialYearLabel(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return String.format("%02d-%02d", startYear % 100, (startYear + 1) % 100);
    }
}
