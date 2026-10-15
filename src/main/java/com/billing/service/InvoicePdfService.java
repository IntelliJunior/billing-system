package com.billing.service;

import com.billing.exception.ResourceNotFoundException;
import com.billing.gst.AmountInWords;
import com.billing.gst.GstStates;
import com.billing.model.Customer;
import com.billing.model.Invoice;
import com.billing.model.InvoiceItem;
import com.billing.model.Payment;
import com.billing.model.Tenant;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.TenantRepository;
import com.billing.security.CurrentTenant;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;
    private final TenantRepository tenantRepository;
    private final CurrentTenant currentTenant;

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, new Color(37, 99, 235));
    private static final Font COMPANY = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);
    private static final Font SMALL_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f);
    private static final Font MUTED = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
    private static final Color HEADER_BG = new Color(243, 244, 246);

    private static final float PAGE_MARGIN = 40f;
    private static final float CONTENT_WIDTH = PageSize.A4.getWidth() - 2 * PAGE_MARGIN; // 515
    private static final float FOOTER_BOTTOM = 30f;   // distance of the footer text from the page bottom
    private static final float LINE_HEIGHT = 13f;
    // Banner: stretched across the page. Change these two numbers to adjust it.
    private static final float MAX_BANNER_HEIGHT = 230f; // tallest the banner may get (points; A4 is 842 high)
    private static final float BANNER_SIDE_INSET = 0f;   // 0 = edge to edge; use e.g. 15 if your printer clips the edges

    private record FooterLine(String text, Font font) {
    }

    @Transactional(readOnly = true)
    public byte[] generate(Long id) {
        Long tenantId = currentTenant.id();
        // Only invoices of the signed-in tenant can be printed
        Invoice inv = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + id));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        List<FooterLine> footer = footerLines(tenant);
        float bottomMargin = footerTop(footer.size()) + 10f;   // keep the page content above the footer

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, bottomMargin);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterEvent(footer));
            doc.open();

            addHeader(doc, tenant);

            boolean gst = Boolean.TRUE.equals(inv.getGstInvoice());
            if (gst) {
                addGstBody(doc, inv, tenant);
            } else {
                addPlainBody(doc, inv);
            }
            addPayments(doc, inv, gst);
            if (gst) {
                addSignature(doc, tenant);
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not generate invoice PDF", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------- GST tax invoice

    private void addGstBody(Document doc, Invoice inv, Tenant tenant) throws DocumentException {
        boolean inter = Boolean.TRUE.equals(inv.getInterState());
        Customer c = inv.getCustomer();

        // Title and invoice details
        PdfPTable top = new PdfPTable(2);
        top.setWidthPercentage(100);
        top.addCell(cell("TAX INVOICE", TITLE, Element.ALIGN_LEFT, false, 2));
        Paragraph meta = new Paragraph();
        meta.setAlignment(Element.ALIGN_RIGHT);
        meta.add(new Chunk("Invoice No: " + inv.getInvoiceNumber() + "\n", BOLD));
        meta.add(new Chunk("Date: " + inv.getIssueDate()
                + (inv.getDueDate() != null ? "     Due: " + inv.getDueDate() : ""), NORMAL));
        PdfPCell metaCell = new PdfPCell();
        metaCell.addElement(meta);
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setPadding(2);
        top.addCell(metaCell);
        doc.add(top);

        // Seller and buyer side by side
        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.setSpacingBefore(6f);
        parties.addCell(partyCell("SELLER", tenant.getName(), tenant.getAddress(),
                inv.getSupplierGstin(), inv.getSupplierStateCode()));
        parties.addCell(partyCell("BILL TO", c.getName(), c.getAddress(),
                notBlank(inv.getCustomerGstin()) ? inv.getCustomerGstin() : null, inv.getCustomerStateCode()));
        doc.add(parties);

        PdfPTable supply = new PdfPTable(1);
        supply.setWidthPercentage(100);
        supply.addCell(cell("Place of supply: " + GstStates.label(inv.getPlaceOfSupplyStateCode())
                + "      Supply: " + (inter ? "Inter-state (IGST)" : "Intra-state (CGST + SGST)"),
                SMALL_BOLD, Element.ALIGN_LEFT, true, 3));
        doc.add(supply);
        doc.add(gap(6f));

        // Line items
        PdfPTable items = new PdfPTable(9);
        items.setWidthPercentage(100);
        items.setWidths(new float[]{0.6f, 3.2f, 1.3f, 0.8f, 1.5f, 1.6f, 0.9f, 1.4f, 1.7f});
        String[] heads = {"#", "Description", "HSN/SAC", "Qty", "Rate (Rs.)", "Taxable (Rs.)", "GST %", "Tax (Rs.)", "Total (Rs.)"};
        for (int i = 0; i < heads.length; i++) {
            PdfPCell h = cell(heads[i], SMALL_BOLD, i <= 2 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT, true, 3);
            h.setBackgroundColor(HEADER_BG);
            items.addCell(h);
        }
        int n = 1;
        for (InvoiceItem it : inv.getItems()) {
            BigDecimal taxable = it.getTaxableValue() != null
                    ? it.getTaxableValue() : it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity()));
            BigDecimal tax = nz(it.getCgstAmount()).add(nz(it.getSgstAmount())).add(nz(it.getIgstAmount()));
            items.addCell(cell(String.valueOf(n++), SMALL, Element.ALIGN_LEFT, true, 3));
            items.addCell(cell(nullToEmpty(it.getDescription()), SMALL, Element.ALIGN_LEFT, true, 3));
            items.addCell(cell(notBlank(it.getHsnCode()) ? it.getHsnCode() : "-", SMALL, Element.ALIGN_LEFT, true, 3));
            items.addCell(cell(String.valueOf(it.getQuantity()), SMALL, Element.ALIGN_RIGHT, true, 3));
            items.addCell(cell(amount(it.getUnitPrice()), SMALL, Element.ALIGN_RIGHT, true, 3));
            items.addCell(cell(amount(taxable), SMALL, Element.ALIGN_RIGHT, true, 3));
            items.addCell(cell(rate(it.getTaxPercent()), SMALL, Element.ALIGN_RIGHT, true, 3));
            items.addCell(cell(amount(tax), SMALL, Element.ALIGN_RIGHT, true, 3));
            items.addCell(cell(amount(it.getLineTotal()), SMALL, Element.ALIGN_RIGHT, true, 3));
        }
        doc.add(items);
        doc.add(gap(6f));

        // Tax summary (right aligned)
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(48);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        addTotalRow(totals, "Taxable value", money(inv.getSubtotal()), false);
        if (inter) {
            addTotalRow(totals, "IGST", money(inv.getIgstAmount()), false);
        } else {
            addTotalRow(totals, "CGST", money(inv.getCgstAmount()), false);
            addTotalRow(totals, "SGST", money(inv.getSgstAmount()), false);
        }
        addTotalRow(totals, "Total tax", money(inv.getTaxAmount()), false);
        addTotalRow(totals, "Invoice total", money(inv.getTotalAmount()), true);
        addTotalRow(totals, "Paid", money(inv.getAmountPaid()), false);
        addTotalRow(totals, "Balance due", money(inv.getBalanceDue()), true);
        doc.add(totals);

        Paragraph words = new Paragraph("Amount in words: " + AmountInWords.rupees(inv.getTotalAmount()), SMALL_BOLD);
        words.setSpacingBefore(4f);
        doc.add(words);
    }

    private PdfPCell partyCell(String heading, String name, String address, String gstin, String stateCode) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(heading + "\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.GRAY)));
        p.add(new Chunk(nullToEmpty(name) + "\n", BOLD));
        if (notBlank(address)) p.add(new Chunk(address + "\n", SMALL));
        p.add(new Chunk("GSTIN: " + (notBlank(gstin) ? gstin : "Unregistered") + "\n", SMALL));
        p.add(new Chunk("State: " + GstStates.label(stateCode), SMALL));
        PdfPCell cell = new PdfPCell();
        cell.addElement(p);
        cell.setPadding(5);
        return cell;
    }

    private void addSignature(Document doc, Tenant tenant) throws DocumentException {
        PdfPTable sign = new PdfPTable(1);
        sign.setWidthPercentage(40);
        sign.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sign.setSpacingBefore(8f);
        PdfPCell sc = new PdfPCell();
        sc.setBorder(Rectangle.NO_BORDER);
        sc.addElement(new Paragraph("For " + nullToEmpty(tenant.getName()), SMALL_BOLD));
        sc.addElement(gap(24f));
        sc.addElement(new Paragraph("Authorised Signatory", SMALL));
        sign.addCell(sc);
        doc.add(sign);
    }

    // ------------------------------------------------------------- invoices without GST

    private void addPlainBody(Document doc, Invoice inv) throws DocumentException {
        // Invoice title and details
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.addCell(cell("INVOICE", TITLE, Element.ALIGN_LEFT, false));
        header.addCell(cell("# " + inv.getInvoiceNumber(), BOLD, Element.ALIGN_RIGHT, false));
        header.addCell(cell("Status: " + inv.getStatus().name().replace('_', ' '), NORMAL, Element.ALIGN_LEFT, false));
        header.addCell(cell("Issued: " + inv.getIssueDate()
                + (inv.getDueDate() != null ? "     Due: " + inv.getDueDate() : ""),
                NORMAL, Element.ALIGN_RIGHT, false));
        doc.add(header);

        // Bill To
        Customer c = inv.getCustomer();
        doc.add(gap(8f));
        doc.add(new Paragraph("Bill To", BOLD));
        doc.add(new Paragraph(nullToEmpty(c.getName()), NORMAL));
        if (notBlank(c.getEmail())) doc.add(new Paragraph(c.getEmail(), NORMAL));
        if (notBlank(c.getPhone())) doc.add(new Paragraph(c.getPhone(), NORMAL));
        if (notBlank(c.getAddress())) doc.add(new Paragraph(c.getAddress(), NORMAL));
        doc.add(gap(8f));

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
        doc.add(gap(8f));

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
    }

    private void addPayments(Document doc, Invoice inv, boolean compact) throws DocumentException {
        if (inv.getPayments().isEmpty()) {
            return;
        }
        int pad = compact ? 3 : 6;
        Font font = compact ? SMALL : NORMAL;
        Font headFont = compact ? SMALL_BOLD : BOLD;

        doc.add(gap(compact ? 6f : 8f));
        doc.add(new Paragraph("Payments", headFont));
        PdfPTable pay = new PdfPTable(3);
        pay.setWidthPercentage(100);
        pay.setSpacingBefore(4f);
        String[] heads = {"Date", "Method", "Amount"};
        for (int i = 0; i < heads.length; i++) {
            PdfPCell h = cell(heads[i], headFont, i == 2 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, true, pad);
            h.setBackgroundColor(HEADER_BG);
            pay.addCell(h);
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
        for (Payment p : inv.getPayments()) {
            pay.addCell(cell(p.getPaidAt() != null ? p.getPaidAt().format(fmt) : "", font, Element.ALIGN_LEFT, true, pad));
            pay.addCell(cell(p.getMethod().name().replace('_', ' '), font, Element.ALIGN_LEFT, true, pad));
            pay.addCell(cell(money(p.getAmount()), font, Element.ALIGN_RIGHT, true, pad));
        }
        doc.add(pay);
    }

    // ------------------------------------------------------------- header and footer

    /**
     * Banner stretched across the full page width at the very top of the first page;
     * if there is no (usable) banner, the tenant name and contact line.
     */
    private void addHeader(Document doc, Tenant tenant) throws DocumentException {
        if (tenant.getBanner() != null) {
            try {
                Image img = Image.getInstance(tenant.getBanner());
                float pageWidth = doc.getPageSize().getWidth();
                float pageHeight = doc.getPageSize().getHeight();

                img.scaleToFit(pageWidth - 2 * BANNER_SIDE_INSET, MAX_BANNER_HEIGHT);
                float w = img.getScaledWidth();
                float h = img.getScaledHeight();
                img.setAbsolutePosition((pageWidth - w) / 2, pageHeight - h);
                doc.add(img);   // drawn at the top edge of the page, outside the normal text flow

                // Push the text down so it starts below the banner
                float gap = h - PAGE_MARGIN + 12f;
                if (gap > 0) {
                    PdfPTable spacer = new PdfPTable(1);
                    spacer.setWidthPercentage(100);
                    PdfPCell spacerCell = new PdfPCell(new Phrase(" "));
                    spacerCell.setBorder(Rectangle.NO_BORDER);
                    spacerCell.setFixedHeight(gap);
                    spacer.addCell(spacerCell);
                    doc.add(spacer);
                }
                return;
            } catch (IOException | DocumentException e) {
                // fall through to the text header
            }
        }
        Paragraph name = new Paragraph(nullToEmpty(tenant.getName()), COMPANY);
        name.setAlignment(Element.ALIGN_CENTER);
        doc.add(name);

        List<String> parts = new ArrayList<>();
        if (notBlank(tenant.getAddress())) parts.add(tenant.getAddress());
        if (notBlank(tenant.getMobile())) parts.add("Mobile: " + tenant.getMobile());
        if (notBlank(tenant.getEmail())) parts.add(tenant.getEmail());
        if (!parts.isEmpty()) {
            Paragraph contact = new Paragraph(String.join("  |  ", parts), MUTED);
            contact.setAlignment(Element.ALIGN_CENTER);
            doc.add(contact);
        }
        doc.add(gap(8f));
    }

    /** Bank details printed at the bottom of every page. Empty when the tenant has none. */
    private List<FooterLine> footerLines(Tenant t) {
        List<FooterLine> lines = new ArrayList<>();
        if (notBlank(t.getAccountHolder())) lines.add(new FooterLine("Account Holder: " + t.getAccountHolder(), NORMAL));
        if (notBlank(t.getBankName())) {
            String bank = "Bank: " + t.getBankName() + (notBlank(t.getBranch()) ? ", " + t.getBranch() : "");
            lines.add(new FooterLine(bank, NORMAL));
        } else if (notBlank(t.getBranch())) {
            lines.add(new FooterLine("Branch: " + t.getBranch(), NORMAL));
        }
        if (notBlank(t.getAccountNumber())) lines.add(new FooterLine("Account No: " + t.getAccountNumber(), NORMAL));
        if (notBlank(t.getIfsc())) lines.add(new FooterLine("IFSC: " + t.getIfsc(), NORMAL));
        if (notBlank(t.getUpiId())) lines.add(new FooterLine("UPI: " + t.getUpiId(), NORMAL));
        if (!lines.isEmpty()) {
            lines.add(0, new FooterLine("Bank Details", BOLD));
        }
        return lines;
    }

    /** Height (from the page bottom) of the line above the footer text. */
    private static float footerTop(int lineCount) {
        return FOOTER_BOTTOM + Math.max(lineCount, 1) * LINE_HEIGHT + 8f;
    }

    private static Paragraph gap(float height) {
        Paragraph p = new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 1));
        p.setLeading(height);
        return p;
    }

    private static class FooterEvent extends PdfPageEventHelper {
        private final List<FooterLine> lines;

        FooterEvent(List<FooterLine> lines) {
            this.lines = lines;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float left = document.leftMargin();
            float right = document.getPageSize().getWidth() - document.rightMargin();
            float top = footerTop(lines.size());

            cb.saveState();
            cb.setLineWidth(0.5f);
            cb.setColorStroke(Color.LIGHT_GRAY);
            cb.moveTo(left, top);
            cb.lineTo(right, top);
            cb.stroke();
            cb.restoreState();

            float y = top - LINE_HEIGHT;
            for (FooterLine line : lines) {
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(line.text(), line.font()), left, y, 0);
                y -= LINE_HEIGHT;
            }

            // Thank-you note, right aligned on the first footer line
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Thank you for your business.", MUTED), right, top - LINE_HEIGHT, 0);
        }
    }

    // ------------------------------------------------------------- small helpers

    private PdfPCell cell(String text, Font font, int align, boolean border) {
        return cell(text, font, align, border, 6);
    }

    private PdfPCell cell(String text, Font font, int align, boolean border, int padding) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(padding);
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
        t.addCell(cell(label, f, Element.ALIGN_LEFT, false, 3));
        t.addCell(cell(value, f, Element.ALIGN_RIGHT, false, 3));
    }

    // Standard PDF fonts can't draw the rupee symbol, so the PDF uses "Rs."
    private String money(BigDecimal v) {
        return "Rs. " + amount(v);
    }

    private String amount(BigDecimal v) {
        return String.format(Locale.US, "%,.2f", v == null ? BigDecimal.ZERO : v);
    }

    private String rate(Double percent) {
        return BigDecimal.valueOf(percent == null ? 0.0 : percent).stripTrailingZeros().toPlainString() + "%";
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }
    private boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
