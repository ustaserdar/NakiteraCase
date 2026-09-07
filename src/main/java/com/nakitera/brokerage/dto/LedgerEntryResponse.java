package com.nakitera.brokerage.dto;

import com.nakitera.brokerage.domain.BalanceLedger;
import com.nakitera.brokerage.domain.LedgerReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Immutable balance movement for a customer asset")
public record LedgerEntryResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "customer-1") String customerId,
        @Schema(example = "TRY") String assetName,
        @Schema(example = "0") BigDecimal sizeDelta,
        @Schema(example = "-2505.000000") BigDecimal usableDelta,
        @Schema(example = "1") Long orderId,
        @Schema(example = "RESERVE") LedgerReason reason,
        @Schema(example = "2026-05-05T12:00:00Z") Instant createdAt
) {
    public static LedgerEntryResponse from(BalanceLedger entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getCustomerId(),
                entry.getAssetName(),
                entry.getSizeDelta(),
                entry.getUsableDelta(),
                entry.getOrderId(),
                entry.getReason(),
                entry.getCreatedAt()
        );
    }
}
