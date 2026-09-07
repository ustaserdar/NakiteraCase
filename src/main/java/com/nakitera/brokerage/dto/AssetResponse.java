package com.nakitera.brokerage.dto;

import com.nakitera.brokerage.domain.Asset;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Customer asset balance, including TRY")
public record AssetResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "customer-1") String customerId,
        @Schema(example = "TRY") String assetName,
        @Schema(example = "100000.000000") BigDecimal size,
        @Schema(example = "100000.000000") BigDecimal usableSize
) {
    public static AssetResponse from(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getCustomerId(),
                asset.getAssetName(),
                asset.getSize(),
                asset.getUsableSize()
        );
    }
}
