package com.nakitera.brokerage.dto;

import com.nakitera.brokerage.domain.Order;
import com.nakitera.brokerage.domain.OrderSide;
import com.nakitera.brokerage.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Persisted order")
public record OrderResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "customer-1") String customerId,
        @Schema(example = "THYAO") String assetName,
        @Schema(example = "BUY") OrderSide orderSide,
        @Schema(example = "10") BigDecimal size,
        @Schema(example = "250.50") BigDecimal price,
        @Schema(example = "PENDING") OrderStatus status,
        @Schema(example = "2026-05-05T12:00:00Z") Instant createDate
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getAssetName(),
                order.getOrderSide(),
                order.getSize(),
                order.getPrice(),
                order.getStatus(),
                order.getCreateDate()
        );
    }
}
