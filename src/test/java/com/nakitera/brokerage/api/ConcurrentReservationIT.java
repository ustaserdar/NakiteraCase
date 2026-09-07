package com.nakitera.brokerage.api;

import com.nakitera.brokerage.domain.Asset;
import com.nakitera.brokerage.domain.Money;
import com.nakitera.brokerage.domain.Order;
import com.nakitera.brokerage.domain.OrderSide;
import com.nakitera.brokerage.dto.CreateOrderRequest;
import com.nakitera.brokerage.exception.ApiException;
import com.nakitera.brokerage.repository.AssetRepository;
import com.nakitera.brokerage.repository.BalanceLedgerRepository;
import com.nakitera.brokerage.repository.IdempotencyRecordRepository;
import com.nakitera.brokerage.repository.OrderRepository;
import com.nakitera.brokerage.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentReservationIT {

    @Autowired
    private OrderService orderService;

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
        assetRepository.save(new Asset("customer-1", Money.TRY, decimal("100"), decimal("100")));
        assetRepository.save(new Asset("customer-1", "THYAO", decimal("10"), decimal("10")));
    }

    @Test
    void concurrentBuysCannotReserveMoreThanUsableTry() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "customer-1",
                "THYAO",
                OrderSide.BUY,
                new BigDecimal("80"),
                new BigDecimal("1.00")
        );

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Boolean>> tasks = List.of(
                () -> attemptCreate(request),
                () -> attemptCreate(request)
        );

        List<Future<Boolean>> results = pool.invokeAll(tasks);
        pool.shutdown();

        long successes = 0;
        for (Future<Boolean> result : results) {
            if (Boolean.TRUE.equals(result.get())) {
                successes++;
            }
        }

        assertThat(successes).isEqualTo(1);
        Asset tryAsset = assetRepository.findByCustomerIdAndAssetName("customer-1", Money.TRY).orElseThrow();
        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo("20.000000");
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentCancelDoesNotReleaseTwice() throws Exception {
        Order order = orderService.create(new CreateOrderRequest(
                "customer-1",
                "THYAO",
                OrderSide.BUY,
                new BigDecimal("40"),
                new BigDecimal("1.00")
        ));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        tasks.add(() -> attemptCancel(order.getId()));
        tasks.add(() -> attemptCancel(order.getId()));

        List<Future<Boolean>> results = pool.invokeAll(tasks);
        pool.shutdown();

        long successes = 0;
        for (Future<Boolean> result : results) {
            if (Boolean.TRUE.equals(result.get())) {
                successes++;
            }
        }

        assertThat(successes).isEqualTo(1);
        Asset tryAsset = assetRepository.findByCustomerIdAndAssetName("customer-1", Money.TRY).orElseThrow();
        assertThat(tryAsset.getUsableSize()).isEqualByComparingTo("100.000000");
    }

    private boolean attemptCreate(CreateOrderRequest request) {
        try {
            orderService.create(request);
            return true;
        } catch (ApiException | org.springframework.dao.OptimisticLockingFailureException ex) {
            return false;
        }
    }

    private boolean attemptCancel(Long orderId) {
        try {
            orderService.cancel(orderId);
            return true;
        } catch (ApiException | org.springframework.dao.OptimisticLockingFailureException ex) {
            return false;
        }
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(Money.SIZE_SCALE);
    }
}
