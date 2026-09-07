package com.nakitera.brokerage.service;

import com.nakitera.brokerage.domain.Asset;
import com.nakitera.brokerage.domain.BalanceLedger;
import com.nakitera.brokerage.domain.Order;
import com.nakitera.brokerage.domain.OrderSide;
import com.nakitera.brokerage.domain.OrderStatus;
import com.nakitera.brokerage.dto.CreateOrderRequest;
import com.nakitera.brokerage.dto.CreatedOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class BrokerageFacade {

    private final OrderService orderService;
    private final OrderMatchingService orderMatchingService;
    private final AssetService assetService;
    private final LedgerService ledgerService;
    private final AccessControlService accessControlService;
    private final ConcurrentOperationExecutor concurrentOperationExecutor;

    public BrokerageFacade(
            OrderService orderService,
            OrderMatchingService orderMatchingService,
            AssetService assetService,
            LedgerService ledgerService,
            AccessControlService accessControlService,
            ConcurrentOperationExecutor concurrentOperationExecutor
    ) {
        this.orderService = orderService;
        this.orderMatchingService = orderMatchingService;
        this.assetService = assetService;
        this.ledgerService = ledgerService;
        this.accessControlService = accessControlService;
        this.concurrentOperationExecutor = concurrentOperationExecutor;
    }

    public CreatedOrder createOrder(CreateOrderRequest request, String idempotencyKey) {
        accessControlService.assertCanAccessCustomer(request.customerId());
        try {
            return concurrentOperationExecutor.execute(() -> orderService.create(request, idempotencyKey));
        } catch (DataIntegrityViolationException ex) {
            CreatedOrder replay = orderService.findReplay(request, idempotencyKey);
            if (replay != null) {
                return replay;
            }
            throw ex;
        }
    }

    public Order getOrder(Long orderId) {
        Order order = orderService.getById(orderId);
        accessControlService.assertCanAccessOrder(order.getCustomerId());
        return order;
    }

    public Order cancelOrder(Long orderId) {
        Order existing = orderService.getById(orderId);
        accessControlService.assertCanAccessOrder(existing.getCustomerId());
        return concurrentOperationExecutor.execute(() -> orderService.cancel(orderId));
    }

    public List<Order> listOrders(
            String customerId,
            Instant startDate,
            Instant endDate,
            OrderStatus status,
            OrderSide orderSide,
            String assetName
    ) {
        accessControlService.assertCanAccessCustomer(customerId);
        return orderService.list(customerId, startDate, endDate, status, orderSide, assetName);
    }

    public List<Asset> listAssets(String customerId) {
        return assetService.listByCustomer(customerId);
    }

    public List<BalanceLedger> listLedger(String customerId) {
        return ledgerService.listByCustomer(customerId);
    }

    public List<Order> matchOrders(List<Long> orderIds) {
        return concurrentOperationExecutor.execute(() -> orderMatchingService.match(orderIds));
    }
}
