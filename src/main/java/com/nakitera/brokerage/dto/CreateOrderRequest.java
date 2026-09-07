package com.nakitera.brokerage.dto;

import com.nakitera.brokerage.domain.OrderSide;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

@Schema(description = "Request body for creating a stock order")
public record CreateOrderRequest(
        @Schema(description = "Customer who owns the order", example = "customer-1")
        @NotBlank String customerId,
        @Schema(description = "Stock symbol. Must not be TRY.", example = "THYAO")
        @NotBlank
        @Pattern(regexp = "(?i)^(?!TRY$).+", message = "assetName must not be TRY")
        String assetName,
        @Schema(description = "BUY reserves TRY; SELL reserves stock quantity", example = "BUY")
        @NotNull OrderSide orderSide,
        @Schema(description = "Share quantity, greater than zero", example = "10")
        @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "size must be greater than zero")
        @Digits(integer = 13, fraction = 6)
        BigDecimal size,
        @Schema(description = "TRY price per share, greater than zero", example = "250.50")
        @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "price must be greater than zero")
        @Digits(integer = 15, fraction = 4)
        BigDecimal price
) {
}
