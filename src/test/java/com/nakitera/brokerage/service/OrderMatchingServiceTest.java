package com.nakitera.brokerage.service;

import com.nakitera.brokerage.domain.Asset;
import com.nakitera.brokerage.domain.Money;
import com.nakitera.brokerage.domain.Order;
import com.nakitera.brokerage.domain.OrderSide;
import com.nakitera.brokerage.domain.OrderStatus;
import com.nakitera.brokerage.exception.ApiException;
import com.nakitera.brokerage.exception.ErrorCodes;
import com.nakitera.brokerage.repository.AssetRepository;
import com.nakitera.brokerage.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMatchingServiceTest {

    private static final String CUSTOMER = "customer-1";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetService assetService;

    @Mock
    private LedgerService ledgerService;

    private OrderMatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new OrderMatchingService(orderRepository, assetRepository, assetService, ledgerService);
    }

    @Test
    void buyMatchReducesTrySizeAndCreditsStock() {
        Order buy = order(1L, OrderSide.BUY, "10", "250.50");
        Asset tryAsset = new Asset(CUSTOMER, Money.TRY, decimal("10000"), decimal("7495"));
        Asset stock = new Asset(CUSTOMER, "THYAO", decimal("0"), decimal("0"));
        when(orderRepository.findAllById(List.of(1L))).thenReturn(List.of(buy));
        when(assetService.requireTry(CUSTOMER)).thenReturn(tryAsset);
        when(assetRepository.findByCustomerIdAndAssetNameForUpdate(CUSTOMER, "THYAO")).thenReturn(Optional.of(stock));
        when(orderRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Order> matched = matchingService.match(List.of(1L));

        assertThat(matched).allMatch(order -> order.getStatus() == OrderStatus.MATCHED);
        assertThat(tryAsset.getSize()).isEqualByComparingTo("7495.000000");
        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo("7495.000000");
        assertThat(stock.getSize()).isEqualByComparingTo("10.000000");
        assertThat(stock.getUsableSize()).isEqualByComparingTo("10.000000");
    }

    @Test
    void sellMatchReducesStockSizeAndCreditsTry() {
        Order sell = order(2L, OrderSide.SELL, "10", "250.50");
        Asset tryAsset = new Asset(CUSTOMER, Money.TRY, decimal("1000"), decimal("1000"));
        Asset stock = new Asset(CUSTOMER, "THYAO", decimal("100"), decimal("90"));
        when(orderRepository.findAllById(List.of(2L))).thenReturn(List.of(sell));
        when(assetService.requireAsset(CUSTOMER, "THYAO")).thenReturn(stock);
        when(assetService.requireTry(CUSTOMER)).thenReturn(tryAsset);
        when(orderRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        matchingService.match(List.of(2L));

        assertThat(stock.getSize()).isEqualByComparingTo("90.000000");
        assertThat(stock.getUsableSize()).isEqualByComparingTo("90.000000");
        assertThat(tryAsset.getSize()).isEqualByComparingTo("3505.000000");
        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo("3505.000000");
    }

    @Test
    void batchContainingNonPendingOrderIsRejectedWithoutMatching() {
        Order pending = order(1L, OrderSide.BUY, "1", "1.00");
        Order canceled = order(2L, OrderSide.BUY, "1", "1.00");
        canceled.markCanceled();
        when(orderRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(pending, canceled));

        assertThatThrownBy(() -> matchingService.match(List.of(1L, 2L)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCodes.ORDER_NOT_PENDING);

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).saveAll(anyList());
    }

    private Order order(Long id, OrderSide side, String size, String price) {
        Order order = new Order(
                CUSTOMER,
                "THYAO",
                side,
                Money.normalizeSize(new BigDecimal(size)),
                Money.normalizePrice(new BigDecimal(price)),
                OrderStatus.PENDING,
                Instant.parse("2026-05-05T12:00:00Z")
        );
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(Money.SIZE_SCALE);
    }
}
