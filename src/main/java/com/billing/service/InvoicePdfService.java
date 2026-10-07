package com.billing.service;

import com.billing.exception.ResourceNotFoundException;
import com.billing.model.Customer;
import com.billing.model.Invoice;
import com.billing.model.InvoiceItem;
import com.billing.model.Payment;
import com.billing.repository.InvoiceRepository;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, new Color(37, 99, 235));
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font MUTED = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
    private static final Color HEADER_BG = new Color(243, 244, 246);

    @Transactional(readOnly = true)
    public byte[] generate(Long id) {
        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Header: title on the left, invoice details on the right
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.addCell(cell("INVOICE", TITLE, Element.ALIGN_LEFT, false));
            header.addCell(cell("# " + inv.getInvoiceNumber(), BOLD, Element.ALIGN_RIGHT, false));
            header.addCell(cell("Status: " + inv.getStatus().name().replace('_', ' '), NORMAL, Element.ALIGN_LEFT, false));
            header.addCell(cell("Issued: " + inv.getIssueDate(), NORMAL, Element.ALIGN_RIGHT, false));
            header.addCell(cell("", NORMAL, Element.ALIGN_LEFT, false));
            header.addCell(cell(inv.getDueDate() != null ? "Due: " + inv.getDueDate() : "", NORMAL, Element.ALIGN_RIGHT, false));
            doc.add(header);

            // Bill To
            Customer c = inv.getCustomer();
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Bill To", BOLD));
            doc.add(new Paragraph(nullToEmpty(c.getName()), NORMAL));
            if (notBlank(c.getEmail())) doc.add(new Paragraph(c.getEmail(), NORMAL));
            if (notBlank(c.getPhone())) doc.add(new Paragraph(c.getPhone(), NORMAL));
            if (notBlank(c.getAddress())) doc.add(new Paragraph(c.getAddress(), NORMAL));
            doc.add(new Paragraph(" "));

            // Line items
            PdfPTable items = new PdfPTable(5);
            items.setWidthPercentage(100);
            items.setWidths(new float[]{4f, 1f, 1.8f, 1f, 2f});
            items.addCell(headerCell("Description", Element.ALIGN_LEFT));
            items.addCell(headerCell("Qty", Element.ALIGN_RIGHT));
            items.addCell(headerCell("Unit Price", Element.ALIGN_RIGHT));
            items.addCell(headerCell("Tax %", Element.ALIGN_RIGHT));
            items.addCell(headerCell("Line Total", Element.ALIGN_RIGHT));
            for (InvoiceItem it : inv.getItems()) {
                items.addCell(cell(nullToEmpty(it.getDescription()), NORMAL, Element.ALIGN_LEFT, true));
                items.addCell(cell(String.valueOf(it.getQuantity()), NORMAL, Element.ALIGN_RIGHT, true));
                items.addCell(cell(money(it.getUnitPrice()), NORMAL, Element.ALIGN_RIGHT, true));
                items.addCell(cell(it.getTaxPercent() + "%", NORMAL, Element.ALIGN_RIGHT, true));
                items.addCell(cell(money(it.getLineTotal()), NORMAL, Element.ALIGN_RIGHT, true));
            }
            doc.add(items);
            doc.add(new Paragraph(" "));

            // Totals (right aligned)
            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(45);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addTotalRow(totals, "Subtotal", money(inv.getSubtotal()), false);
            addTotalRow(totals, "Tax", money(inv.getTaxAmount()), false);
            addTotalRow(totals, "Total", money(inv.getTotalAmount()), true);
            addTotalRow(totals, "Paid", money(inv.getAmountPaid()), false);
            addTotalRow(totals, "Balance Due", money(inv.getBalanceDue()), true);
            doc.add(totals);

            // Payments
            if (!inv.getPayments().isEmpty()) {
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph("Payments", BOLD));
                PdfPTable pay = new PdfPTable(3);
                pay.setWidthPercentage(100);
                pay.addCell(headerCell("Date", Element.ALIGN_LEFT));
                pay.addCell(headerCell("Method", Element.ALIGN_LEFT));
                pay.addCell(headerCell("Amount", Element.ALIGN_RIGHT));
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
                for (Payment p : inv.getPayments()) {
                    pay.addCell(cell(p.getPaidAt() != null ? p.getPaidAt().format(fmt) : "", NORMAL, Element.ALIGN_LEFT, true));
                    pay.addCell(cell(p.getMethod().name().replace('_', ' '), NORMAL, Element.ALIGN_LEFT, true));
                    pay.addCell(cell(money(p.getAmount()), NORMAL, Element.ALIGN_RIGHT, true));
                }
                doc.add(pay);
            }

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Thank you for your business.", MUTED));
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not generate invoice PDF", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }

    private PdfPCell cell(String text, Font font, int align, boolean border) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(6);
        if (!border) c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private PdfPCell headerCell(String text, int align) {
        PdfPCell c = cell(text, BOLD, align, true);
        c.setBackgroundColor(HEADER_BG);
        return c;
    }

    private void addTotalRow(PdfPTable t, String label, String value, boolean strong) {
        Font f = strong ? BOLD : NORMAL;
        t.addCell(cell(label, f, Element.ALIGN_LEFT, false));
        t.addCell(cell(value, f, Element.ALIGN_RIGHT, false));
    }

    // Standard PDF fonts can't draw the rupee symbol, so the PDF uses "Rs."
    private String money(BigDecimal v) {
        return "Rs. " + String.format(Locale.US, "%,.2f", v == null ? BigDecimal.ZERO : v);
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }
    private boolean notBlank(String s) { return s != null && !s.isBlank(); }
}