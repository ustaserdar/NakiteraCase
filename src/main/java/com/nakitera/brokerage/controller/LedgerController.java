package com.nakitera.brokerage.controller;

import com.nakitera.brokerage.config.OpenApiExamples;
import com.nakitera.brokerage.dto.LedgerEntryResponse;
import com.nakitera.brokerage.service.BrokerageFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger", description = "Immutable reserve / release / match movements")
public class LedgerController {

    private final BrokerageFacade brokerageFacade;

    public LedgerController(BrokerageFacade brokerageFacade) {
        this.brokerageFacade = brokerageFacade;
    }

    @GetMapping
    @Operation(summary = "List balance ledger entries for a customer")
    @ApiResponse(
            responseCode = "200",
            description = "Chronological movements",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = @ExampleObject(name = "Reserve then match", value = OpenApiExamples.LEDGER_LIST)
            )
    )
    public List<LedgerEntryResponse> list(
            @Parameter(description = "Customer identifier", required = true, example = "customer-1")
            @RequestParam @NotBlank String customerId
    ) {
        return brokerageFacade.listLedger(customerId).stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }
}
