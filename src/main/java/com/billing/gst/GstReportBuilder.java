package com.billing.gst;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns a period's invoices into a GST report. Pure Java (no database), so it can be tested on its own.
 *
 * Rules used:
 *  - Cancelled invoices are listed but not counted in any tax figure.
 *  - Invoices made before the GST feature (no GST split) are listed under "not included".
 *  - B2B = the customer has a GSTIN; everything else is B2C.
 *  - B2C large = inter-state B2C invoices above the threshold; the rest of B2C is summarised by state and rate.
 */
public final class GstReportBuilder {

    public record LineIn(String hsn, BigDecimal rate, int quantity,
                         BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal total) {
    }

    public record InvoiceIn(String number, LocalDate date, String customerName, String customerGstin,
                            String placeOfSupplyCode, boolean interState, boolean gstInvoice, boolean cancelled,
                            String status, BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                            BigDecimal tax, BigDecimal total, List<LineIn> lines) {
    }

    private GstReportBuilder() {
    }

    public static GstReport build(GstReport.Header header, List<InvoiceIn> invoices, BigDecimal b2clThreshold) {
        List<InvoiceIn> sorted = new ArrayList<>(invoices);
        sorted.sort(Comparator.comparing(InvoiceIn::date).thenComparing(InvoiceIn::number));

        List<InvoiceIn> gstAll = sorted.stream().filter(InvoiceIn::gstInvoice).toList();
        List<InvoiceIn> active = gstAll.stream().filter(i -> !i.cancelled()).toList();

        // ---- totals and the four segments
        Acc all = new Acc();
        Acc b2bIntra = new Acc();
        Acc b2bInter = new Acc();
        Acc b2cIntra = new Acc();
        Acc b2cInter = new Acc();
        for (InvoiceIn i : active) {
            all.add(i.taxable(), i.cgst(), i.sgst(), i.igst(), i.total());
            Acc seg = isB2b(i) ? (i.interState() ? b2bInter : b2bIntra) : (i.interState() ? b2cInter : b2cIntra);
            seg.add(i.taxable(), i.cgst(), i.sgst(), i.igst(), i.total());
        }
        List<GstReport.SegmentRow> segments = List.of(
                new GstReport.SegmentRow("B2B - same state (CGST + SGST)", b2bIntra.toAmounts()),
                new GstReport.SegmentRow("B2B - other state (IGST)", b2bInter.toAmounts()),
                new GstReport.SegmentRow("B2C - same state (CGST + SGST)", b2cIntra.toAmounts()),
                new GstReport.SegmentRow("B2C - other state (IGST)", b2cInter.toAmounts()));

        // ---- rate-wise, 3B style
        Map<BigDecimal, Acc> rateMap = new TreeMap<>();
        Acc outward = new Acc();
        BigDecimal nilExempt = BigDecimal.ZERO;
        for (InvoiceIn i : active) {
            for (LineIn l : i.lines()) {
                rateMap.computeIfAbsent(rate(l), k -> new Acc()).add(l.taxable(), l.cgst(), l.sgst(), l.igst(), l.total());
                if (rate(l).signum() > 0) {
                    outward.add(l.taxable(), l.cgst(), l.sgst(), l.igst(), l.total());
                } else {
                    nilExempt = nilExempt.add(n(l.taxable()));
                }
            }
        }
        List<GstReport.RateRow> byRate = new ArrayList<>();
        rateMap.forEach((r, acc) -> byRate.add(new GstReport.RateRow(r, acc.toAmounts())));

        // ---- inter-state supplies to unregistered persons, state by state
        Map<String, BigDecimal[]> unreg = new TreeMap<>();
        for (InvoiceIn i : active) {
            if (!isB2b(i) && i.interState()) {
                BigDecimal[] a = unreg.computeIfAbsent(GstStates.label(i.placeOfSupplyCode()),
                        k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                a[0] = a[0].add(n(i.taxable()));
                a[1] = a[1].add(n(i.igst()));
            }
        }
        List<GstReport.StateRow> unregRows = new ArrayList<>();
        unreg.forEach((state, a) -> unregRows.add(new GstReport.StateRow(state, money(a[0]), money(a[1]))));

        // ---- sales register
        List<GstReport.RegisterRow> register = new ArrayList<>();
        for (InvoiceIn i : active) {
            register.add(new GstReport.RegisterRow(i.date(), i.number(), i.customerName(),
                    isB2b(i) ? i.customerGstin() : "",
                    GstStates.label(i.placeOfSupplyCode()), isB2b(i) ? "B2B" : "B2C",
                    i.interState() ? "Inter-state" : "Intra-state",
                    money(i.taxable()), money(i.cgst()), money(i.sgst()), money(i.igst()),
                    money(i.tax()), money(i.total()), i.status()));
        }

        // ---- B2B, B2C large, B2C small
        List<GstReport.B2bRow> b2b = new ArrayList<>();
        List<GstReport.B2cLargeRow> b2cLarge = new ArrayList<>();
        Map<String, Acc> small = new TreeMap<>();
        Map<String, String[]> smallKeys = new LinkedHashMap<>();
        for (InvoiceIn i : active) {
            Map<BigDecimal, Acc> perRate = perRate(i);
            if (isB2b(i)) {
                perRate.forEach((r, a) -> {
                    GstReport.Amounts x = a.toAmounts();
                    b2b.add(new GstReport.B2bRow(i.customerGstin(), i.customerName(), i.number(), i.date(),
                            money(i.total()), GstStates.label(i.placeOfSupplyCode()), r,
                            x.taxable(), x.igst(), x.cgst(), x.sgst()));
                });
            } else if (i.interState() && n(i.total()).compareTo(b2clThreshold) > 0) {
                perRate.forEach((r, a) -> {
                    GstReport.Amounts x = a.toAmounts();
                    b2cLarge.add(new GstReport.B2cLargeRow(i.number(), i.date(), money(i.total()),
                            GstStates.label(i.placeOfSupplyCode()), r, x.taxable(), x.igst()));
                });
            } else {
                String supply = i.interState() ? "Inter-state" : "Intra-state";
                String place = GstStates.label(i.placeOfSupplyCode());
                perRate.forEach((r, a) -> {
                    String key = place + "|" + supply + "|" + r.toPlainString();
                    small.computeIfAbsent(key, k -> new Acc()).addAmounts(a);
                    smallKeys.putIfAbsent(key, new String[]{place, supply, r.toPlainString()});
                });
            }
        }
        List<GstReport.B2cSmallRow> b2cSmall = new ArrayList<>();
        small.forEach((key, acc) -> {
            String[] k = smallKeys.get(key);
            GstReport.Amounts x = acc.toAmounts();
            b2cSmall.add(new GstReport.B2cSmallRow(k[0], k[1], new BigDecimal(k[2]),
                    x.taxable(), x.igst(), x.cgst(), x.sgst()));
        });

        // ---- HSN summary
        Map<String, HsnAcc> hsnMap = new TreeMap<>();
        for (InvoiceIn i : active) {
            for (LineIn l : i.lines()) {
                String code = (l.hsn() == null || l.hsn().isBlank()) ? "NA" : l.hsn();
                HsnAcc h = hsnMap.computeIfAbsent(code + "|" + rate(l).toPlainString(),
                        k -> new HsnAcc(code, rate(l)));
                h.quantity = h.quantity.add(BigDecimal.valueOf(l.quantity()));
                h.acc.add(l.taxable(), l.cgst(), l.sgst(), l.igst(), l.total());
            }
        }
        List<GstReport.HsnRow> hsn = new ArrayList<>();
        hsnMap.values().forEach(h -> {
            GstReport.Amounts x = h.acc.toAmounts();
            hsn.add(new GstReport.HsnRow(h.code, h.rate, h.quantity, x.taxable(), x.igst(), x.cgst(), x.sgst(), x.value()));
        });

        // ---- documents issued (cancelled ones are counted here)
        Map<String, List<InvoiceIn>> bySeries = new TreeMap<>();
        for (InvoiceIn i : gstAll) {
            String series = i.number().contains("/") ? i.number().substring(0, i.number().lastIndexOf('/') + 1) : "(no series)";
            bySeries.computeIfAbsent(series, k -> new ArrayList<>()).add(i);
        }
        List<GstReport.DocRow> documents = new ArrayList<>();
        Comparator<String> numberOrder = Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());
        bySeries.forEach((series, list) -> {
            List<String> numbers = list.stream().map(InvoiceIn::number).sorted(numberOrder).toList();
            int cancelled = (int) list.stream().filter(InvoiceIn::cancelled).count();
            documents.add(new GstReport.DocRow(series, numbers.get(0), numbers.get(numbers.size() - 1),
                    list.size(), cancelled, list.size() - cancelled));
        });

        // ---- invoices left out of the figures
        List<GstReport.NotIncludedRow> notIncluded = new ArrayList<>();
        for (InvoiceIn i : sorted) {
            if (!i.gstInvoice()) {
                notIncluded.add(new GstReport.NotIncludedRow(i.number(), i.date(), i.customerName(), money(i.total()),
                        i.cancelled() ? "Cancelled (made before GST was switched on)" : "Made before GST was switched on (no CGST/SGST/IGST split)"));
            } else if (i.cancelled()) {
                notIncluded.add(new GstReport.NotIncludedRow(i.number(), i.date(), i.customerName(), money(i.total()), "Cancelled"));
            }
        }

        return new GstReport(header, all.toAmounts(), segments, byRate, outward.toAmounts(), money(nilExempt),
                unregRows, register, b2b, b2cLarge, b2cSmall, hsn, documents, notIncluded, b2clThreshold);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isB2b(InvoiceIn i) {
        return i.customerGstin() != null && !i.customerGstin().isBlank();
    }

    private static BigDecimal rate(LineIn l) {
        return (l.rate() == null ? BigDecimal.ZERO : l.rate()).setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<BigDecimal, Acc> perRate(InvoiceIn i) {
        Map<BigDecimal, Acc> m = new TreeMap<>();
        for (LineIn l : i.lines()) {
            m.computeIfAbsent(rate(l), k -> new Acc()).add(l.taxable(), l.cgst(), l.sgst(), l.igst(), l.total());
        }
        return m;
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal money(BigDecimal v) {
        return n(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static final class HsnAcc {
        final String code;
        final BigDecimal rate;
        BigDecimal quantity = BigDecimal.ZERO;
        final Acc acc = new Acc();

        HsnAcc(String code, BigDecimal rate) {
            this.code = code;
            this.rate = rate;
        }
    }

    private static final class Acc {
        int count;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;

        void add(BigDecimal t, BigDecimal c, BigDecimal s, BigDecimal i, BigDecimal v) {
            count++;
            taxable = taxable.add(n(t));
            cgst = cgst.add(n(c));
            sgst = sgst.add(n(s));
            igst = igst.add(n(i));
            value = value.add(n(v));
        }

        void addAmounts(Acc other) {
            count += other.count;
            taxable = taxable.add(other.taxable);
            cgst = cgst.add(other.cgst);
            sgst = sgst.add(other.sgst);
            igst = igst.add(other.igst);
            value = value.add(other.value);
        }

        GstReport.Amounts toAmounts() {
            BigDecimal tax = cgst.add(igst).add(sgst);
            return new GstReport.Amounts(count, money(taxable), money(cgst), money(sgst), money(igst), money(tax), money(value));
        }
    }
}
