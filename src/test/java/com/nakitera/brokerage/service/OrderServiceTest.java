package com.nakitera.brokerage.service;

import com.nakitera.brokerage.domain.Asset;
import com.nakitera.brokerage.domain.Money;
import com.nakitera.brokerage.domain.Order;
import com.nakitera.brokerage.domain.OrderSide;
import com.nakitera.brokerage.domain.OrderStatus;
import com.nakitera.brokerage.dto.CreateOrderRequest;
import com.nakitera.brokerage.exception.ApiException;
import com.nakitera.brokerage.exception.ErrorCodes;
import com.nakitera.brokerage.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-05T12:00:00Z");
    private static final String CUSTOMER = "customer-1";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AssetService assetService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private com.nakitera.brokerage.repository.IdempotencyRecordRepository idempotencyRecordRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                assetService,
                ledgerService,
                idempotencyRecordRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void buyWithSufficientTryCreatesPendingOrderAndReservesExactAmount() {
        Asset tryAsset = tryAsset("10000.000000", "10000.000000");
        when(assetService.requireTry(CUSTOMER)).thenReturn(tryAsset);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.create(buy("THYAO", "10", "250.50"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCreateDate()).isEqualTo(NOW);
        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo("7495.000000");
        assertThat(tryAsset.getSize()).isEqualByComparingTo("10000.000000");
        assertThat(Money.requiredTry(order.getSize(), order.getPrice())).isEqualByComparingTo("2505.000000");
    }

    @Test
    void buyWithExactlySufficientTryLeavesZeroUsable() {
        Asset tryAsset = tryAsset("2505.000000", "2505.000000");
        when(assetService.requireTry(CUSTOMER)).thenReturn(tryAsset);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.create(buy("THYAO", "10", "250.50"));

        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void buyWithInsufficientTryIsRejectedWithoutPersisting() {
        Asset tryAsset = tryAsset("100.000000", "100.000000");
        when(assetService.requireTry(CUSTOMER)).thenReturn(tryAsset);

        assertThatThrownBy(() -> orderService.create(buy("THYAO", "10", "250.50")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.INSUFFICIENT_USABLE_BALANCE);

        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo("100.000000");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void sellWithSufficientStockCreatesPendingOrderAndReservesQuantity() {
        Asset stock = stock("100.000000", "100.000000");
        when(assetService.requireAsset(CUSTOMER, "THYAO")).thenReturn(stock);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.create(sell("THYAO", "10", "250.50"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(stock.getUsableSize()).isEqualByComparingTo("90.000000");
        assertThat(stock.getSize()).isEqualByComparingTo("100.000000");
    }

    @Test
    void sellWithExactlySufficientStockLeavesZeroUsable() {
        Asset stock = stock("10.000000", "10.000000");
        when(assetService.requireAsset(CUSTOMER, "THYAO")).thenReturn(stock);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.create(sell("THYAO", "10", "1.00"));

        assertThat(stock.getUsableSize()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sellWithInsufficientStockIsRejectedWithoutPersisting() {
        Asset stock = stock("5.000000", "5.000000");
        when(assetService.requireAsset(CUSTOMER, "THYAO")).thenReturn(stock);

        assertThatThrownBy(() -> orderService.create(sell("THYAO", "10", "1.00")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.INSUFFICIENT_USABLE_BALANCE);

        assertThat(stock.getUsableSize()).isEqualByComparingTo("5.000000");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void missingAssetIsPropagated() {
        when(assetService.requireTry(CUSTOMER)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND,
                ErrorCodes.ASSET_NOT_FOUND,
                "missing"
        ));

        assertThatThrownBy(() -> orderService.create(buy("THYAO", "1", "1.00")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.ASSET_NOT_FOUND);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void zeroSizeIsRejected() {
        assertThatThrownBy(() -> orderService.create(buy("THYAO", "0", "10.00")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void negativePriceIsRejected() {
        assertThatThrownBy(() -> orderService.create(buy("THYAO", "1", "-1.00")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
    }

    @Test
    void tryAsTradedAssetIsRejected() {
        assertThatThrownBy(() -> orderService.create(buy("TRY", "1", "1.00")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.VALIDATION_ERROR);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelPendingBuyRestoresReservedTry() {
        Order order = pending(OrderSide.BUY, "10", "250.50");
        Asset tryAsset = tryAsset("10000.000000", "7495.000000");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(assetService.requireTry(CUSTOMER)).thenReturn(tryAsset);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order canceled = orderService.cancel(1L);

        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo("10000.000000");
    }

    @Test
    void cancelPendingSellRestoresReservedStock() {
        Order order = pending(OrderSide.SELL, "10", "250.50");
        Asset stock = stock("100.000000", "90.000000");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(assetService.requireAsset(CUSTOMER, "THYAO")).thenReturn(stock);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.cancel(1L);

        assertThat(stock.getUsableSize()).isEqualByComparingTo("100.000000");
    }

    @Test
    void matchedOrderCannotBeCanceled() {
        Order order = pending(OrderSide.BUY, "1", "1.00");
        order.markMatched();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.ORDER_NOT_PENDING);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void canceledOrderCannotBeCanceledAgain() {
        Order order = pending(OrderSide.BUY, "1", "1.00");
        order.markCanceled();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.ORDER_NOT_PENDING);
        verify(assetService, never()).requireTry(any());
    }

    @Test
    void unknownOrderReturnsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancel(99L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.ORDER_NOT_FOUND);
    }

    @Test
    void invalidDateRangeIsRejected() {
        Instant start = Instant.parse("2026-05-31T00:00:00Z");
        Instant end = Instant.parse("2026-05-01T00:00:00Z");

        assertThatThrownBy(() -> orderService.list(CUSTOMER, start, end, null, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.INVALID_DATE_RANGE);
    }

    @Test
    void createPersistsNormalizedUppercaseAssetName() {
        Asset tryAsset = tryAsset("1000.000000", "1000.000000");
        when(assetService.requireTry(CUSTOMER)).thenReturn(tryAsset);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.create(buy("thyao", "1", "10.00"));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getAssetName()).isEqualTo("THYAO");
    }

    private CreateOrderRequest buy(String asset, String size, String price) {
        return new CreateOrderRequest(CUSTOMER, asset, OrderSide.BUY, new BigDecimal(size), new BigDecimal(price));
    }

    private CreateOrderRequest sell(String asset, String size, String price) {
        return new CreateOrderRequest(CUSTOMER, asset, OrderSide.SELL, new BigDecimal(size), new BigDecimal(price));
    }

    private Asset tryAsset(String size, String usable) {
        return new Asset(CUSTOMER, Money.TRY, new BigDecimal(size), new BigDecimal(usable));
    }

    private Asset stock(String size, String usable) {
        return new Asset(CUSTOMER, "THYAO", new BigDecimal(size), new BigDecimal(usable));
    }

    private Order pending(OrderSide side, String size, String price) {
        return new Order(
                CUSTOMER,
                "THYAO",
                side,
                Money.normalizeSize(new BigDecimal(size)),
                Money.normalizePrice(new BigDecimal(price)),
                OrderStatus.PENDING,
                NOW
        );
    }
}
