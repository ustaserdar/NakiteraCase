package com.nakitera.brokerage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Admin request to match a set of PENDING orders")
public record MatchOrdersRequest(
        @Schema(description = "Order ids to match in one transaction", example = "[1, 2]")
        @NotEmpty List<Long> orderIds
) {
}
