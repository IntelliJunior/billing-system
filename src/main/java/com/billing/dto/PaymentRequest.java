package com.billing.dto;

import com.billing.model.Payment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotNull
    @Positive(message = "Payment amount must be greater than zero")
    private BigDecimal amount;

    private Payment.PaymentMethod method = Payment.PaymentMethod.CASH;

    private String referenceNote;
}
