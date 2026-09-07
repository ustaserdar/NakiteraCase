package com.nakitera.brokerage.controller;

import com.nakitera.brokerage.config.OpenApiExamples;
import com.nakitera.brokerage.domain.OrderSide;
import com.nakitera.brokerage.domain.OrderStatus;
import com.nakitera.brokerage.dto.CreateOrderRequest;
import com.nakitera.brokerage.dto.CreatedOrder;
import com.nakitera.brokerage.dto.ErrorResponse;
import com.nakitera.brokerage.dto.OrderResponse;
import com.nakitera.brokerage.service.BrokerageFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Create, list, and cancel customer stock orders")
public class OrderController {

    private final BrokerageFacade brokerageFacade;

    public OrderController(BrokerageFacade brokerageFacade) {
        this.brokerageFacade = brokerageFacade;
    }

    @PostMapping
    @Operation(
            summary = "Create a BUY or SELL order",
            description = "Reserves usable TRY (BUY) or stock quantity (SELL) and persists a PENDING order. "
                    + "Optional Idempotency-Key replays the same order without a second reservation."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Order created",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrderResponse.class),
                    examples = @ExampleObject(name = "Created PENDING order", value = OpenApiExamples.ORDER_CREATED)
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "Idempotent replay of an existing order",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrderResponse.class),
                    examples = @ExampleObject(name = "Replayed order", value = OpenApiExamples.ORDER_CREATED)
            )
    )
    @ApiResponse(
            responseCode = "409",
            description = "Insufficient usable balance",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(name = "Insufficient TRY", value = OpenApiExamples.INSUFFICIENT_BALANCE)
            )
    )
    public ResponseEntity<OrderResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Order to create. assetName cannot be TRY.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreateOrderRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "BUY THYAO",
                                            summary = "Reserve TRY for a buy order",
                                            value = OpenApiExamples.BUY_ORDER
                                    ),
                                    @ExampleObject(
                                            name = "SELL THYAO",
                                            summary = "Reserve stock for a sell order",
                                            value = OpenApiExamples.SELL_ORDER
                                    )
                            }
                    )
            )
            @Valid @RequestBody CreateOrderRequest request,
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    description = "Optional. Same key + same body returns the original order without a second reservation.",
                    example = "buy-thyao-001"
            )
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey
    ) {
        CreatedOrder created = brokerageFacade.createOrder(request, idempotencyKey);
        HttpStatus status = created.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(OrderResponse.from(created.order()));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get a single order by id")
    @ApiResponse(
            responseCode = "200",
            description = "Order found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrderResponse.class),
                    examples = @ExampleObject(name = "Pending order", value = OpenApiExamples.ORDER_CREATED)
            )
    )
    public OrderResponse get(
            @Parameter(description = "Order identifier", required = true, example = "1")
            @PathVariable Long orderId
    ) {
        return OrderResponse.from(brokerageFacade.getOrder(orderId));
    }

    @GetMapping
    @Operation(summary = "List a customer's orders in a date range")
    @ApiResponse(
            responseCode = "200",
            description = "Matching orders, newest first",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(name = "Order list", value = OpenApiExamples.ORDER_LIST)
            )
    )
    public List<OrderResponse> list(
            @Parameter(description = "Customer identifier", required = true, example = "customer-1")
            @RequestParam @NotBlank String customerId,
            @Parameter(description = "Inclusive start (ISO-8601 UTC)", required = true, example = "2026-01-01T00:00:00Z")
            @RequestParam @NotNull Instant startDate,
            @Parameter(description = "Inclusive end (ISO-8601 UTC)", required = true, example = "2026-12-31T23:59:59Z")
            @RequestParam @NotNull Instant endDate,
            @Parameter(description = "Optional status filter", example = "PENDING")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Optional side filter", example = "BUY")
            @RequestParam(required = false) OrderSide orderSide,
            @Parameter(description = "Optional stock symbol filter", example = "THYAO")
            @RequestParam(required = false) String assetName
    ) {
        return brokerageFacade.listOrders(customerId, startDate, endDate, status, orderSide, assetName)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    @DeleteMapping("/{orderId}")
    @Operation(
            summary = "Cancel a PENDING order",
            description = "Soft-cancels the order and releases the reserved balance. The row is retained."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Order canceled",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = OrderResponse.class),
                    examples = @ExampleObject(name = "Canceled order", value = OpenApiExamples.ORDER_CANCELED)
            )
    )
    public OrderResponse cancel(
            @Parameter(description = "Order identifier", required = true, example = "1")
            @PathVariable Long orderId
    ) {
        return OrderResponse.from(brokerageFacade.cancelOrder(orderId));
    }
}
