package com.billing.controller;

import com.billing.dto.StockMovementRequest;
import com.billing.model.Product;
import com.billing.model.StockMovement;
import com.billing.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/stock")
    public List<Product> stock(@RequestParam(defaultValue = "false") boolean lowOnly) {
        return inventoryService.stockList(lowOnly);
    }

    @GetMapping("/movements")
    public List<StockMovement> movements(@RequestParam(required = false) Long productId) {
        return inventoryService.movements(productId);
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovement record(@Valid @RequestBody StockMovementRequest request) {
        return inventoryService.recordMovement(request);
    }
}
