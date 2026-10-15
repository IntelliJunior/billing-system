package com.billing.gst;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Writes a GST report as a landscape A4 PDF. */
public final class GstPdfWriter {

    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(37, 99, 235));
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 7.5f);
    private static final Font CELL_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f);
    private static final Font MUTED = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final Color HEAD_BG = new Color(243, 244, 246);
    private static final int MAX_NOT_INCLUDED_ROWS = 100;

    private GstPdfWriter() {
    }

    public static byte[] write(GstReport r) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer(r.header().tenantName() + "  |  GST report  |  " + r.header().periodLabel()));
            doc.open();

            GstReport.Header h = r.header();
            doc.add(new Paragraph("GST Report", H1));
            doc.add(new Paragraph(h.tenantName() + "   |   GSTIN: " + h.gstin() + "   |   " + h.stateLabel(), BODY));
            doc.add(new Paragraph("Period: " + h.periodLabel() + "  (" + date(h.from()) + " to " + date(h.to()) + ")", BODY));
            doc.add(new Paragraph("Generated: " + h.generatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH))
                    + "   |   Based on invoice date. Cancelled invoices and invoices made before GST was switched on are not included.", MUTED));

            section(doc, "Totals");
            GstReport.Amounts t = r.totals();
            table(doc, new String[]{"Invoices", "Taxable value", "CGST", "SGST", "IGST", "Total tax", "Invoice value"},
                    new float[]{1, 2, 2, 2, 2, 2, 2},
                    List.<Object[]>of(
                            new Object[]{
                                    t.count(), t.taxable(), t.cgst(), t.sgst(),
                                    t.igst(), t.tax(), t.value()
                            }
                    ));

            section(doc, "By customer type and supply");
            List<Object[]> seg = new ArrayList<>();
            for (GstReport.SegmentRow s : r.segments()) {
                GstReport.Amounts a = s.amounts();
                seg.add(new Object[]{s.label(), a.count(), a.taxable(), a.cgst(), a.sgst(), a.igst(), a.tax(), a.value()});
            }
            table(doc, new String[]{"", "Invoices", "Taxable value", "CGST", "SGST", "IGST", "Total tax", "Invoice value"},
                    new float[]{4, 1, 2, 2, 2, 2, 2, 2}, seg);

            section(doc, "By GST rate");
            List<Object[]> byRate = new ArrayList<>();
            for (GstReport.RateRow x : r.byRate()) {
                GstReport.Amounts a = x.amounts();
                byRate.add(new Object[]{x.rate().stripTrailingZeros().toPlainString() + "%", a.count(), a.taxable(), a.cgst(), a.sgst(), a.igst(), a.tax(), a.value()});
            }
            table(doc, new String[]{"Rate", "Lines", "Taxable value", "CGST", "SGST", "IGST", "Total tax", "Value with tax"},
                    new float[]{2, 1, 2, 2, 2, 2, 2, 2}, byRate);

            section(doc, "Outward supplies (GSTR-3B table 3.1 style)");
            GstReport.Amounts o = r.outwardTaxable();
            List<Object[]> outward = new ArrayList<>();
            outward.add(new Object[]{"(a) Outward taxable supplies (rate above 0%)", o.taxable(), o.igst(), o.cgst(), o.sgst()});
            outward.add(new Object[]{"(c) Other outward supplies (0% rate: nil rated or exempt)", r.nilExemptTaxable(), "", "", ""});
            table(doc, new String[]{"Nature of supply", "Taxable value", "IGST", "CGST", "SGST"},
                    new float[]{6, 2, 2, 2, 2}, outward);

            if (!r.unregisteredInterState().isEmpty()) {
                section(doc, "Inter-state supplies to unregistered persons, state by state");
                List<Object[]> unreg = new ArrayList<>();
                for (GstReport.StateRow s : r.unregisteredInterState()) {
                    unreg.add(new Object[]{s.state(), s.taxable(), s.igst()});
                }
                table(doc, new String[]{"Place of supply", "Taxable value", "IGST"}, new float[]{5, 2, 2}, unreg);
            }

            section(doc, "Sales register");
            List<Object[]> reg = new ArrayList<>();
            for (GstReport.RegisterRow x : r.register()) {
                reg.add(new Object[]{date(x.date()), x.invoiceNumber(), x.customer(), x.gstin(), x.placeOfSupply(),
                        x.type(), x.taxable(), x.cgst(), x.sgst(), x.igst(), x.tax(), x.value()});
            }
            if (reg.isEmpty()) {
                doc.add(new Paragraph("No GST invoices in this period.", BODY));
            } else {
                table(doc, new String[]{"Date", "Invoice no", "Customer", "GSTIN", "Place of supply", "Type",
                                "Taxable", "CGST", "SGST", "IGST", "Tax", "Invoice value"},
                        new float[]{1.5f, 2.2f, 2.8f, 2.6f, 2.4f, 0.9f, 1.8f, 1.4f, 1.4f, 1.4f, 1.4f, 1.9f}, reg);
            }

            if (!r.hsn().isEmpty()) {
                section(doc, "HSN / SAC summary");
                List<Object[]> hsn = new ArrayList<>();
                for (GstReport.HsnRow x : r.hsn()) {
                    hsn.add(new Object[]{x.hsn(), x.rate().stripTrailingZeros().toPlainString() + "%", x.quantity().stripTrailingZeros().toPlainString(),
                            x.taxable(), x.igst(), x.cgst(), x.sgst(), x.value()});
                }
                table(doc, new String[]{"HSN / SAC", "Rate", "Quantity", "Taxable value", "IGST", "CGST", "SGST", "Total value"},
                        new float[]{2, 1, 1.2f, 2, 2, 2, 2, 2}, hsn);
            }

            if (!r.documents().isEmpty()) {
                section(doc, "Invoices issued");
                List<Object[]> docs = new ArrayList<>();
                for (GstReport.DocRow x : r.documents()) {
                    docs.add(new Object[]{x.series(), x.from(), x.to(), x.total(), x.cancelled(), x.net()});
                }
                table(doc, new String[]{"Series", "From", "To", "Total issued", "Cancelled", "Net issued"},
                        new float[]{2, 2.5f, 2.5f, 1.2f, 1.2f, 1.2f}, docs);
            }

            if (!r.notIncluded().isEmpty()) {
                section(doc, "Not included in the figures (" + r.notIncluded().size() + ")");
                List<Object[]> ni = new ArrayList<>();
                for (GstReport.NotIncludedRow x : r.notIncluded()) {
                    if (ni.size() >= MAX_NOT_INCLUDED_ROWS) {
                        break;
                    }
                    ni.add(new Object[]{x.invoiceNumber(), date(x.date()), x.customer(), x.value(), x.reason()});
                }
                table(doc, new String[]{"Invoice no", "Date", "Customer", "Invoice value", "Reason"},
                        new float[]{2.4f, 1.4f, 2.6f, 1.6f, 5}, ni);
                if (r.notIncluded().size() > MAX_NOT_INCLUDED_ROWS) {
                    doc.add(new Paragraph("Showing the first " + MAX_NOT_INCLUDED_ROWS + ". The Excel report lists all of them.", MUTED));
                }
            }

            section(doc, "Notes");
            String[] notes = {
                    "Figures come from the invoices in this system, by invoice date. Input tax credit is not included because purchases are not recorded.",
                    "B2B means the customer has a GSTIN. B2C Large (inter-state B2C invoices above Rs. "
                            + r.b2clThreshold().stripTrailingZeros().toPlainString() + ") and the B2B / B2C detail tables are in the Excel report.",
                    "Nil-rated, exempt and non-GST supplies are not told apart (all are 0% lines).",
                    "Credit/debit notes, reverse charge, e-invoicing and exports are not covered.",
                    "This report is a calculation aid. Check it with your Chartered Accountant before filing any return."};
            for (String n : notes) {
                doc.add(new Paragraph("- " + n, BODY));
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not generate the GST report PDF", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- helpers

    private static void section(Document doc, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, H2);
        p.setSpacingBefore(12f);
        p.setSpacingAfter(4f);
        p.setKeepTogether(true);
        doc.add(p);
    }

    private static void table(Document doc, String[] heads, float[] widths, List<Object[]> rows) throws DocumentException {
        PdfPTable t = new PdfPTable(heads.length);
        t.setWidthPercentage(100);
        t.setWidths(widths);
        t.setHeaderRows(1);
        for (String h : heads) {
            PdfPCell c = new PdfPCell(new Phrase(h, CELL_BOLD));
            c.setBackgroundColor(HEAD_BG);
            c.setPadding(3);
            t.addCell(c);
        }
        for (Object[] row : rows) {
            for (Object v : row) {
                boolean number = v instanceof BigDecimal || v instanceof Integer;
                PdfPCell c = new PdfPCell(new Phrase(text(v), CELL));
                c.setPadding(3);
                c.setHorizontalAlignment(number ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                t.addCell(c);
            }
        }
        doc.add(t);
    }

    private static String text(Object v) {
        if (v == null) return "";
        if (v instanceof BigDecimal b) return String.format(Locale.US, "%,.2f", b);
        return v.toString();
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    private static final class Footer extends PdfPageEventHelper {
        private final String text;

        Footer(String text) {
            this.text = text;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = 22;
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(text, MUTED), document.leftMargin(), y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Page " + writer.getPageNumber(), MUTED),
                    document.getPageSize().getWidth() - document.rightMargin(), y, 0);
        }
    }
}
