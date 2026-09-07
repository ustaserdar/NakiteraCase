package com.nakitera.brokerage.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nakitera.brokerage.domain.Asset;
import com.nakitera.brokerage.domain.Money;
import com.nakitera.brokerage.domain.OrderStatus;
import com.nakitera.brokerage.repository.AssetRepository;
import com.nakitera.brokerage.repository.BalanceLedgerRepository;
import com.nakitera.brokerage.repository.IdempotencyRecordRepository;
import com.nakitera.brokerage.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrokerageApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private BalanceLedgerRepository balanceLedgerRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
        balanceLedgerRepository.deleteAll();
        orderRepository.deleteAll();
        assetRepository.deleteAll();
        assetRepository.save(new Asset("customer-1", Money.TRY, decimal("100000"), decimal("100000")));
        assetRepository.save(new Asset("customer-1", "THYAO", decimal("100"), decimal("100")));
        assetRepository.save(new Asset("customer-2", Money.TRY, decimal("50000"), decimal("50000")));
        assetRepository.save(new Asset("customer-2", "THYAO", decimal("20"), decimal("20")));
    }

    @Test
    void unauthenticatedAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/assets").param("customerId", "customer-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/assets")
                        .param("customerId", "customer-1")
                        .with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateListAndCancelOrder() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-1", "THYAO", "BUY", "10", "250.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        long orderId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant end = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(get("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .param("customerId", "customer-1")
                        .param("startDate", start.toString())
                        .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(orderId));

        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        Asset tryAsset = assetRepository.findByCustomerIdAndAssetName("customer-1", Money.TRY).orElseThrow();
        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo("100000.000000");
    }

    @Test
    void listingIsCustomerScopedAndSupportsEmptyAndDateRules() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-1", "THYAO", "BUY", "1", "10.00")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-2", "THYAO", "BUY", "1", "10.00")))
                .andExpect(status().isCreated());

        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);

        mockMvc.perform(get("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .param("customerId", "customer-1")
                        .param("startDate", start.toString())
                        .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerId").value("customer-1"));

        mockMvc.perform(get("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .param("customerId", "customer-1")
                        .param("startDate", "2020-01-01T00:00:00Z")
                        .param("endDate", "2020-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .param("customerId", "customer-1")
                        .param("startDate", "2026-05-31T00:00:00Z")
                        .param("endDate", "2026-05-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void assetListingNeverExposesAnotherCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/assets")
                        .with(httpBasic("admin", "admin123"))
                        .param("customerId", "customer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].customerId").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("customer-1")
                )));
    }

    @Test
    void customerCannotAccessAnotherCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/assets")
                        .with(httpBasic("customer-1", "customer123"))
                        .param("customerId", "customer-2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("customer-1", "customer123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-2", "THYAO", "BUY", "1", "10.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotCallMatchEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/match")
                        .with(httpBasic("customer-1", "customer123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[1]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationRejectsTryAssetAndNonPositiveValues() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-1", "TRY", "BUY", "1", "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-1", "THYAO", "BUY", "0", "10.00")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"customer-1\",\"assetName\":\"THYAO\",\"orderSide\":\"HOLD\",\"size\":1,\"price\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanMatchPendingOrdersAndBatchRollsBackOnInvalid() throws Exception {
        MvcResult buy = mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-1", "THYAO", "BUY", "10", "100.00")))
                .andExpect(status().isCreated())
                .andReturn();
        long buyId = objectMapper.readTree(buy.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/orders/match")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[" + buyId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("MATCHED"));

        Asset tryAsset = assetRepository.findByCustomerIdAndAssetName("customer-1", Money.TRY).orElseThrow();
        Asset stock = assetRepository.findByCustomerIdAndAssetName("customer-1", "THYAO").orElseThrow();
        assertThat(tryAsset.getSize()).isEqualByComparingTo("99000.000000");
        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo("99000.000000");
        assertThat(stock.getSize()).isEqualByComparingTo("110.000000");
        assertThat(stock.getUsableSize()).isEqualByComparingTo("110.000000");

        MvcResult pending = mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-1", "THYAO", "SELL", "5", "100.00")))
                .andExpect(status().isCreated())
                .andReturn();
        long pendingId = objectMapper.readTree(pending.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/orders/match")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[" + pendingId + "," + buyId + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PENDING"));

        assertThat(orderRepository.findById(pendingId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void insufficientBalanceReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-1", "THYAO", "BUY", "1000000", "10.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_USABLE_BALANCE"));
    }

    @Test
    void missingAssetReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-1", "GARAN", "SELL", "1", "10.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));
    }

    @Test
    void swaggerUiAndOpenApiArePublicAndIncludeRequestExamples() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.requestBody.content['application/json'].examples['BUY THYAO'].value.orderSide").value("BUY"))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.requestBody.content['application/json'].examples['SELL THYAO'].value.orderSide").value("SELL"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/orders/match'].post.requestBody.content['application/json'].examples['Match two orders'].value.orderIds[0]").value(1))
                .andExpect(jsonPath("$.paths['/api/v1/assets'].get.parameters[0].example").value("customer-1"));

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void getOrderByIdAndIdempotentCreateAndLedger() throws Exception {
        String body = orderJson("customer-1", "THYAO", "BUY", "10", "250.50");
        MvcResult first = mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .header("Idempotency-Key", "buy-thyao-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .header("Idempotency-Key", "buy-thyao-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        mockMvc.perform(post("/api/v1/orders")
                        .with(httpBasic("admin", "admin123"))
                        .header("Idempotency-Key", "buy-thyao-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson("customer-1", "THYAO", "BUY", "1", "10.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .with(httpBasic("customer-2", "customer123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ledger")
                        .with(httpBasic("admin", "admin123"))
                        .param("customerId", "customer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("RESERVE"))
                .andExpect(jsonPath("$[0].assetName").value("TRY"))
                .andExpect(jsonPath("$[0].orderId").value(orderId))
                .andExpect(jsonPath("$[0].usableDelta").value(org.hamcrest.Matchers.closeTo(-2505.0, 0.000001)));

        Asset tryAsset = assetRepository.findByCustomerIdAndAssetName("customer-1", Money.TRY).orElseThrow();
        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo("97495.000000");
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    private String orderJson(String customerId, String asset, String side, String size, String price) throws Exception {
        return objectMapper.createObjectNode()
                .put("customerId", customerId)
                .put("assetName", asset)
                .put("orderSide", side)
                .put("size", new BigDecimal(size))
                .put("price", new BigDecimal(price))
                .toString();
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(Money.SIZE_SCALE);
    }
}
