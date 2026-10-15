package com.billing.controller;

import com.billing.gst.GstStates;
import com.billing.model.GstRate;
import com.billing.service.GstRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reference lists the screens need. Available to every signed-in user. */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final GstRateService gstRateService;

    @GetMapping("/gst-states")
    public List<GstStates.State> states() {
        return GstStates.all();
    }

    @GetMapping("/gst-rates")
    public List<GstRate> rates() {
        return gstRateService.all();
    }
}
