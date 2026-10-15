package com.billing.gst;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/** Writes a GST report as an Excel workbook with one sheet per report. */
public final class GstExcelWriter {

    private GstExcelWriter() {
    }

    public static byte[] write(GstReport r) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            summary(wb, st, r);
            register(wb, st, r);
            b2b(wb, st, r);
            b2cLarge(wb, st, r);
            b2cSmall(wb, st, r);
            hsn(wb, st, r);
            documents(wb, st, r);
            notIncluded(wb, st, r);
            notes(wb, st, r);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ---------------------------------------------------------------- sheets

    private static void summary(XSSFWorkbook wb, Styles st, GstReport r) {
        W w = new W(wb.createSheet("Summary"), st);
        GstReport.Header h = r.header();
        w.title("GST Report");
        w.row("Business", h.tenantName());
        w.row("GSTIN", h.gstin());
        w.row("State", h.stateLabel());
        w.row("Period", h.periodLabel());
        w.row("Dates", h.from(), h.to());
        w.row("Generated", h.generatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH)));
        w.row("Basis", "Invoice date. Cancelled invoices and invoices made before GST was switched on are not included.");
        w.blank();

        w.section("Totals");
        w.header("", "Invoices", "Taxable value", "CGST", "SGST", "IGST", "Total tax", "Invoice value");
        w.bold("All GST invoices", r.totals().count(), r.totals().taxable(), r.totals().cgst(), r.totals().sgst(),
                r.totals().igst(), r.totals().tax(), r.totals().value());
        w.blank();

        w.section("By customer type and supply");
        w.header("", "Invoices", "Taxable value", "CGST", "SGST", "IGST", "Total tax", "Invoice value");
        for (GstReport.SegmentRow s : r.segments()) {
            GstReport.Amounts a = s.amounts();
            w.row(s.label(), a.count(), a.taxable(), a.cgst(), a.sgst(), a.igst(), a.tax(), a.value());
        }
        w.blank();

        w.section("By GST rate");
        w.header("Rate", "Lines", "Taxable value", "CGST", "SGST", "IGST", "Total tax", "Value with tax");
        for (GstReport.RateRow x : r.byRate()) {
            GstReport.Amounts a = x.amounts();
            w.row(rate(x.rate()), a.count(), a.taxable(), a.cgst(), a.sgst(), a.igst(), a.tax(), a.value());
        }
        w.blank();

        w.section("Outward supplies (GSTR-3B table 3.1 style)");
        w.header("Nature of supply", "", "Taxable value", "IGST", "CGST", "SGST");
        GstReport.Amounts o = r.outwardTaxable();
        w.row("(a) Outward taxable supplies (rate above 0%)", "", o.taxable(), o.igst(), o.cgst(), o.sgst());
        w.row("(c) Other outward supplies (0% rate: nil rated or exempt)", "", r.nilExemptTaxable(), null, null, null);
        w.blank();

        w.section("Inter-state supplies to unregistered persons, state by state (table 3.2 style)");
        w.header("Place of supply", "", "Taxable value", "IGST");
        if (r.unregisteredInterState().isEmpty()) {
            w.row("None in this period");
        }
        for (GstReport.StateRow s : r.unregisteredInterState()) {
            w.row(s.state(), "", s.taxable(), s.igst());
        }
        w.blank();

        w.row("Not included", r.notIncluded().size() + " invoice(s) - see the 'Not included' sheet");
        w.row("Input tax credit", "Not included. Purchases are not recorded in this system.");
        w.finish();
    }

    private static void register(XSSFWorkbook wb, Styles st, GstReport r) {
        W w = new W(wb.createSheet("Sales Register"), st);
        w.header("Date", "Invoice no", "Customer", "Customer GSTIN", "Place of supply", "Type", "Supply",
                "Taxable value", "CGST", "SGST", "IGST", "Total tax", "Invoice value", "Status");
        BigDecimal t = BigDecimal.ZERO, c = t, s = t, i = t, tax = t, v = t;
        for (GstReport.RegisterRow x : r.register()) {
            w.row(x.date(), x.invoiceNumber(), x.customer(), x.gstin(), x.placeOfSupply(), x.type(), x.supply(),
                    x.taxable(), x.cgst(), x.sgst(), x.igst(), x.tax(), x.value(), x.status());
            t = t.add(x.taxable());
            c = c.add(x.cgst());
            s = s.add(x.sgst());
            i = i.add(x.igst());
            tax = tax.add(x.tax());
            v = v.add(x.value());
        }
        w.bold("Total", "", "", "", "", "", "", t, c, s, i, tax, v, "");
        w.freezeHeader(1);
        w.finish();
    }

    private static void b2b(XSSFWorkbook wb, Styles st, GstReport r) {
        W w = new W(wb.createSheet("B2B"), st);
        w.title("B2B invoices (customer has a GSTIN), rate by rate");
        w.header("Customer GSTIN", "Customer", "Invoice no", "Date", "Invoice value", "Place of supply", "Rate %",
                "Taxable value", "IGST", "CGST", "SGST");
        for (GstReport.B2bRow x : r.b2b()) {
            w.row(x.gstin(), x.customer(), x.invoiceNumber(), x.date(), x.invoiceValue(), x.placeOfSupply(),
                    x.rate(), x.taxable(), x.igst(), x.cgst(), x.sgst());
        }
        w.finish();
    }

    private static void b2cLarge(XSSFWorkbook wb, Styles st, GstReport r) {
        W w = new W(wb.createSheet("B2C Large"), st);
        w.title("Inter-state B2C invoices above Rs. " + r.b2clThreshold().stripTrailingZeros().toPlainString());
        w.header("Invoice no", "Date", "Invoice value", "Place of supply", "Rate %", "Taxable value", "IGST");
        for (GstReport.B2cLargeRow x : r.b2cLarge()) {
            w.row(x.invoiceNumber(), x.date(), x.invoiceValue(), x.placeOfSupply(), x.rate(), x.taxable(), x.igst());
        }
        w.finish();
    }

    private static void b2cSmall(XSSFWorkbook wb, Styles st, GstReport r) {
        W w = new W(wb.createSheet("B2C Small"), st);
        w.title("Other B2C supplies, totalled by state and rate");
        w.header("Place of supply", "Supply", "Rate %", "Taxable value", "IGST", "CGST", "SGST");
        for (GstReport.B2cSmallRow x : r.b2cSmall()) {
            w.row(x.placeOfSupply(), x.supply(), x.rate(), x.taxable(), x.igst(), x.cgst(), x.sgst());
        }
        w.finish();
    }

    private static void hsn(XSSFWorkbook wb, Styles st, GstReport r) {
        W w = new W(wb.createSheet("HSN Summary"), st);
        w.header("HSN / SAC", "Rate %", "Quantity", "Taxable value", "IGST", "CGST", "SGST", "Total value");
        for (GstReport.HsnRow x : r.hsn()) {
            w.row(x.hsn(), x.rate(), x.quantity(), x.taxable(), x.igst(), x.cgst(), x.sgst(), x.value());
        }
        w.finish();
    }

    private static void documents(XSSFWorkbook wb, Styles st, GstReport r) {
        W w = new W(wb.createSheet("Documents"), st);
        w.title("Invoices issued in the period");
        w.header("Series", "From", "To", "Total issued", "Cancelled", "Net issued");
        for (GstReport.DocRow x : r.documents()) {
            w.row(x.series(), x.from(), x.to(), x.total(), x.cancelled(), x.net());
        }
        w.finish();
    }

    private static void notIncluded(XSSFWorkbook wb, Styles st, GstReport r) {
        W w = new W(wb.createSheet("Not included"), st);
        w.title("Invoices left out of the figures");
        w.header("Invoice no", "Date", "Customer", "Invoice value", "Reason");
        for (GstReport.NotIncludedRow x : r.notIncluded()) {
            w.row(x.invoiceNumber(), x.date(), x.customer(), x.value(), x.reason());
        }
        w.finish();
    }

    private static void notes(XSSFWorkbook wb, Styles st, GstReport r) {
        W w = new W(wb.createSheet("Notes"), st);
        w.title("Notes and assumptions");
        w.row("1. Figures come from the invoices in this system, by invoice date.");
        w.row("2. Cancelled invoices and invoices made before GST was switched on are listed on 'Not included' and are not in any total.");
        w.row("3. B2B means the customer has a GSTIN. Everything else is B2C.");
        w.row("4. B2C Large = inter-state B2C invoices above Rs. " + r.b2clThreshold().stripTrailingZeros().toPlainString()
                + " (a limit set by your administrator).");
        w.row("5. Table 3.1(c) shows lines with a 0% rate. Nil-rated, exempt and non-GST supplies are not told apart.");
        w.row("6. Input tax credit, credit/debit notes, reverse charge, e-invoicing and exports are not covered.");
        w.row("7. This report is a calculation aid. Check it with your Chartered Accountant before filing any return.");
        w.finish();
    }

    // ---------------------------------------------------------------- helpers

    private static String rate(BigDecimal r) {
        return r.stripTrailingZeros().toPlainString() + "%";
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle section;
        final CellStyle bold;
        final CellStyle header;
        final CellStyle money;
        final CellStyle moneyBold;
        final CellStyle date;

        Styles(XSSFWorkbook wb) {
            DataFormat df = wb.createDataFormat();

            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            title = wb.createCellStyle();
            title.setFont(titleFont);

            section = wb.createCellStyle();
            section.setFont(boldFont);

            bold = wb.createCellStyle();
            bold.setFont(boldFont);

            header = wb.createCellStyle();
            header.setFont(boldFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            header.setWrapText(true);

            money = wb.createCellStyle();
            money.setDataFormat(df.getFormat("#,##0.00"));

            moneyBold = wb.createCellStyle();
            moneyBold.setDataFormat(df.getFormat("#,##0.00"));
            moneyBold.setFont(boldFont);

            date = wb.createCellStyle();
            date.setDataFormat(df.getFormat("dd-mmm-yyyy"));
        }
    }

    /** Small helper that writes rows one after another and sizes the columns from the content. */
    private static final class W {
        final Sheet sheet;
        final Styles st;
        final int[] widths = new int[24];
        int next = 0;

        W(Sheet sheet, Styles st) {
            this.sheet = sheet;
            this.st = st;
        }

        void blank() {
            next++;
        }

        void title(String text) {
            Row row = sheet.createRow(next++);
            Cell c = row.createCell(0);
            c.setCellValue(text);
            c.setCellStyle(st.title);
            next++;
        }

        void section(String text) {
            Row row = sheet.createRow(next++);
            Cell c = row.createCell(0);
            c.setCellValue(text);
            c.setCellStyle(st.section);
        }

        void header(Object... values) {
            Row row = sheet.createRow(next++);
            for (int i = 0; i < values.length; i++) {
                Cell c = row.createCell(i);
                String text = values[i] == null ? "" : values[i].toString();
                c.setCellValue(text);
                c.setCellStyle(st.header);
                widths[i] = Math.max(widths[i], text.length());
            }
        }

        void row(Object... values) {
            put(values, false);
        }

        void bold(Object... values) {
            put(values, true);
        }

        void freezeHeader(int rows) {
            sheet.createFreezePane(0, rows);
        }

        private void put(Object[] values, boolean bold) {
            Row row = sheet.createRow(next++);
            for (int i = 0; i < values.length && i < widths.length; i++) {
                Object v = values[i];
                if (v == null) {
                    continue;
                }
                Cell c = row.createCell(i);
                if (v instanceof BigDecimal b) {
                    c.setCellValue(b.doubleValue());
                    c.setCellStyle(bold ? st.moneyBold : st.money);
                    widths[i] = Math.max(widths[i], 14);
                } else if (v instanceof Integer n) {
                    c.setCellValue(n);
                    if (bold) c.setCellStyle(st.bold);
                    widths[i] = Math.max(widths[i], 8);
                } else if (v instanceof LocalDate d) {
                    c.setCellValue(Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                    c.setCellStyle(st.date);
                    widths[i] = Math.max(widths[i], 12);
                } else {
                    String s = v.toString();
                    c.setCellValue(s);   // stored as text, so values like "=1+1" are never treated as formulas
                    if (bold) c.setCellStyle(st.bold);
                    widths[i] = Math.max(widths[i], Math.min(s.length(), 60));
                }
            }
        }

        void finish() {
            for (int i = 0; i < widths.length; i++) {
                if (widths[i] > 0) {
                    sheet.setColumnWidth(i, Math.min(60, Math.max(8, widths[i] + 2)) * 256);
                }
            }
        }
    }
}
