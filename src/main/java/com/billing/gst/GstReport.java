package com.billing.gst;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Everything a GST report shows, ready to be displayed or written to Excel / PDF. */
public record GstReport(
        Header header,
        Amounts totals,
        List<SegmentRow> segments,
        List<RateRow> byRate,
        Amounts outwardTaxable,          // 3.1(a): lines with a rate above 0
        BigDecimal nilExemptTaxable,     // 3.1(c): lines with a 0% rate
        List<StateRow> unregisteredInterState,
        List<RegisterRow> register,
        List<B2bRow> b2b,
        List<B2cLargeRow> b2cLarge,
        List<B2cSmallRow> b2cSmall,
        List<HsnRow> hsn,
        List<DocRow> documents,
        List<NotIncludedRow> notIncluded,
        BigDecimal b2clThreshold) {

    public record Header(String tenantName, String gstin, String stateLabel,
                         LocalDate from, LocalDate to, String periodLabel, LocalDateTime generatedAt) {
    }

    public record Amounts(int count, BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                          BigDecimal tax, BigDecimal value) {
    }

    public record SegmentRow(String label, Amounts amounts) {
    }

    public record RateRow(BigDecimal rate, Amounts amounts) {
    }

    public record StateRow(String state, BigDecimal taxable, BigDecimal igst) {
    }

    public record RegisterRow(LocalDate date, String invoiceNumber, String customer, String gstin,
                              String placeOfSupply, String type, String supply,
                              BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                              BigDecimal tax, BigDecimal value, String status) {
    }

    public record B2bRow(String gstin, String customer, String invoiceNumber, LocalDate date,
                         BigDecimal invoiceValue, String placeOfSupply, BigDecimal rate,
                         BigDecimal taxable, BigDecimal igst, BigDecimal cgst, BigDecimal sgst) {
    }

    public record B2cLargeRow(String invoiceNumber, LocalDate date, BigDecimal invoiceValue,
                              String placeOfSupply, BigDecimal rate, BigDecimal taxable, BigDecimal igst) {
    }

    public record B2cSmallRow(String placeOfSupply, String supply, BigDecimal rate,
                              BigDecimal taxable, BigDecimal igst, BigDecimal cgst, BigDecimal sgst) {
    }

    public record HsnRow(String hsn, BigDecimal rate, BigDecimal quantity, BigDecimal taxable,
                         BigDecimal igst, BigDecimal cgst, BigDecimal sgst, BigDecimal value) {
    }

    public record DocRow(String series, String from, String to, int total, int cancelled, int net) {
    }

    public record NotIncludedRow(String invoiceNumber, LocalDate date, String customer,
                                 BigDecimal value, String reason) {
    }
}
