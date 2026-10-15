package com.billing.controller;

import com.billing.gst.GstExcelWriter;
import com.billing.gst.GstPdfWriter;
import com.billing.gst.GstReport;
import com.billing.service.GstReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

/** GST reports for the signed-in tenant (needs the GST feature). */
@RestController
@RequestMapping("/api/gst")
@RequiredArgsConstructor
public class GstReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final GstReportService reportService;

    @GetMapping("/report")
    public GstReport report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String label) {
        return reportService.build(from, to, label);
    }

    @GetMapping("/report.xlsx")
    public ResponseEntity<byte[]> excel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String label) {
        GstReport report = reportService.build(from, to, label);
        try {
            return file(GstExcelWriter.write(report), XLSX, "GST-Report-" + from + "-to-" + to + ".xlsx");
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the Excel file", e);
        }
    }

    @GetMapping("/report.pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String label) {
        GstReport report = reportService.build(from, to, label);
        return file(GstPdfWriter.write(report), MediaType.APPLICATION_PDF, "GST-Report-" + from + "-to-" + to + ".pdf");
    }

    private ResponseEntity<byte[]> file(byte[] bytes, MediaType type, String name) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(type)
                .body(bytes);
    }
}
