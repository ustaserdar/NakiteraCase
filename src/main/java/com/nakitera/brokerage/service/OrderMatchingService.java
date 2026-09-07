package com.nakitera.brokerage.service;

import com.nakitera.brokerage.domain.Asset;
import com.nakitera.brokerage.domain.LedgerReason;
import com.nakitera.brokerage.domain.Money;
import com.nakitera.brokerage.domain.Order;
import com.nakitera.brokerage.domain.OrderSide;
import com.nakitera.brokerage.exception.ApiException;
import com.nakitera.brokerage.exception.ErrorCodes;
import com.nakitera.brokerage.repository.AssetRepository;
import com.nakitera.brokerage.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderMatchingService {

    private final OrderRepository orderRepository;
    private final AssetRepository assetRepository;
    private final AssetService assetService;
    private final LedgerService ledgerService;

    public OrderMatchingService(
            OrderRepository orderRepository,
            AssetRepository assetRepository,
            AssetService assetService,
            LedgerService ledgerService
    ) {
        this.orderRepository = orderRepository;
        this.assetRepository = assetRepository;
        this.assetService = assetService;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public List<Order> match(List<Long> orderIds) {
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(orderIds));
        Map<Long, Order> found = orderRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));

        List<Order> orders = new ArrayList<>();
        for (Long id : distinctIds) {
            Order order = found.get(id);
            if (order == null) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCodes.ORDER_NOT_FOUND,
                        "Order %s was not found".formatted(id)
                );
            }
            if (!order.isPending()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        ErrorCodes.ORDER_NOT_PENDING,
                        "Order %s is not PENDING".formatted(id)
                );
            }
            orders.add(order);
        }

        for (Order order : orders) {
            settle(order);
            order.markMatched();
        }
        return orderRepository.saveAll(orders);
    }

    private void settle(Order order) {
        if (order.getOrderSide() == OrderSide.BUY) {
            settleBuy(order);
        } else {
            settleSell(order);
        }
    }

    private void settleBuy(Order order) {
        BigDecimal requiredTry = Money.requiredTry(order.getSize(), order.getPrice());
        Asset tryAsset = assetService.requireTry(order.getCustomerId());
        tryAsset.reduceTotal(requiredTry);
        ledgerService.record(
                order.getCustomerId(),
                Money.TRY,
                requiredTry.negate(),
                BigDecimal.ZERO.setScale(Money.SIZE_SCALE),
                order.getId(),
                LedgerReason.MATCH_DEBIT
        );

        Asset stock = assetRepository
                .findByCustomerIdAndAssetNameForUpdate(order.getCustomerId(), order.getAssetName())
                .orElseGet(() -> assetRepository.save(new Asset(
                        order.getCustomerId(),
                        order.getAssetName(),
                        BigDecimal.ZERO.setScale(Money.SIZE_SCALE),
                        BigDecimal.ZERO.setScale(Money.SIZE_SCALE)
                )));
        stock.credit(order.getSize());
        ledgerService.record(
                order.getCustomerId(),
                order.getAssetName(),
                order.getSize(),
                order.getSize(),
                order.getId(),
                LedgerReason.MATCH_CREDIT
        );
    }

    private void settleSell(Order order) {
        Asset stock = assetService.requireAsset(order.getCustomerId(), order.getAssetName());
        stock.reduceTotal(order.getSize());
        ledgerService.record(
                order.getCustomerId(),
                order.getAssetName(),
                order.getSize().negate(),
                BigDecimal.ZERO.setScale(Money.SIZE_SCALE),
                order.getId(),
                LedgerReason.MATCH_DEBIT
        );

        Asset tryAsset = assetService.requireTry(order.getCustomerId());
        BigDecimal proceeds = Money.requiredTry(order.getSize(), order.getPrice());
        tryAsset.credit(proceeds);
        ledgerService.record(
                order.getCustomerId(),
                Money.TRY,
                proceeds,
                proceeds,
                order.getId(),
                LedgerReason.MATCH_CREDIT
        );
    }
}
