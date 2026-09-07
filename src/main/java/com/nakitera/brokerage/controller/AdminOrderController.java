package com.nakitera.brokerage.controller;

import com.nakitera.brokerage.config.OpenApiExamples;
import com.nakitera.brokerage.dto.MatchOrdersRequest;
import com.nakitera.brokerage.dto.OrderResponse;
import com.nakitera.brokerage.service.BrokerageFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin", description = "Admin-only matching. Requires the ADMIN role.")
public class AdminOrderController {

    private final BrokerageFacade brokerageFacade;

    public AdminOrderController(BrokerageFacade brokerageFacade) {
        this.brokerageFacade = brokerageFacade;
    }

    @PostMapping("/match")
    @Operation(
            summary = "Match PENDING orders",
            description = "Fully executes each selected PENDING order at its own price in one transaction. "
                    + "If any id is missing or not PENDING, the entire batch is rolled back."
    )
    @ApiResponse(
            responseCode = "200",
            description = "All requested orders matched",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(name = "Matched orders", value = OpenApiExamples.MATCHED_ORDERS)
            )
    )
    public List<OrderResponse> match(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "PENDING order ids to match atomically",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MatchOrdersRequest.class),
                            examples = @ExampleObject(
                                    name = "Match two orders",
                                    summary = "Batch match by order id",
                                    value = OpenApiExamples.MATCH_ORDERS
                            )
                    )
            )
            @Valid @RequestBody MatchOrdersRequest request
    ) {
        return brokerageFacade.matchOrders(request.orderIds()).stream()
                .map(OrderResponse::from)
                .toList();
    }
}
