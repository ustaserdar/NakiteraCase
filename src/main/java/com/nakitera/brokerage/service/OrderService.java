package com.nakitera.brokerage.service;

import com.nakitera.brokerage.domain.Asset;
import com.nakitera.brokerage.domain.IdempotencyRecord;
import com.nakitera.brokerage.domain.LedgerReason;
import com.nakitera.brokerage.domain.Money;
import com.nakitera.brokerage.domain.Order;
import com.nakitera.brokerage.domain.OrderSide;
import com.nakitera.brokerage.domain.OrderStatus;
import com.nakitera.brokerage.dto.CreateOrderRequest;
import com.nakitera.brokerage.dto.CreatedOrder;
import com.nakitera.brokerage.exception.ApiException;
import com.nakitera.brokerage.exception.ErrorCodes;
import com.nakitera.brokerage.repository.IdempotencyRecordRepository;
import com.nakitera.brokerage.repository.OrderRepository;
import com.nakitera.brokerage.repository.OrderSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final AssetService assetService;
    private final LedgerService ledgerService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final Clock clock;

    public OrderService(
            OrderRepository orderRepository,
            AssetService assetService,
            LedgerService ledgerService,
            IdempotencyRecordRepository idempotencyRecordRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.assetService = assetService;
        this.ledgerService = ledgerService;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.clock = clock;
    }

    @Transactional
    public Order create(CreateOrderRequest request) {
        return create(request, null).order();
    }

    @Transactional
    public CreatedOrder create(CreateOrderRequest request, String idempotencyKey) {
        if (Money.isTry(request.assetName())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCodes.VALIDATION_ERROR,
                    "assetName must not be TRY"
            );
        }
        if (request.size().compareTo(BigDecimal.ZERO) <= 0 || request.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCodes.VALIDATION_ERROR,
                    "size and price must be greater than zero"
            );
        }

        BigDecimal size = Money.normalizeSize(request.size());
        BigDecimal price = Money.normalizePrice(request.price());
        String assetName = request.assetName().trim().toUpperCase();
        String requestHash = hashRequest(request.customerId(), assetName, request.orderSide(), size, price);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            CreatedOrder replay = replayIfPresent(request.customerId(), idempotencyKey, requestHash);
            if (replay != null) {
                return replay;
            }
        }

        if (request.orderSide() == OrderSide.BUY) {
            reserveBuy(request.customerId(), size, price);
        } else {
            reserveSell(request.customerId(), assetName, size);
        }

        Order order = orderRepository.save(new Order(
                request.customerId(),
                assetName,
                request.orderSide(),
                size,
                price,
                OrderStatus.PENDING,
                Instant.now(clock)
        ));
        recordReservation(order);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyRecordRepository.save(new IdempotencyRecord(
                    request.customerId(),
                    idempotencyKey.trim(),
                    requestHash,
                    order.getId()
            ));
        }
        return new CreatedOrder(order, false);
    }

    @Transactional
    public Order cancel(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCodes.ORDER_NOT_FOUND,
                        "Order %s was not found".formatted(orderId)
                ));
        if (!order.isPending()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCodes.ORDER_NOT_PENDING,
                    "Only PENDING orders can be canceled"
            );
        }
        releaseReservation(order);
        order.markCanceled();
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public CreatedOrder findReplay(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String assetName = request.assetName().trim().toUpperCase();
        String requestHash = hashRequest(
                request.customerId(),
                assetName,
                request.orderSide(),
                Money.normalizeSize(request.size()),
                Money.normalizePrice(request.price())
        );
        return replayIfPresent(request.customerId(), idempotencyKey, requestHash);
    }

    @Transactional(readOnly = true)
    public Order getById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCodes.ORDER_NOT_FOUND,
                        "Order %s was not found".formatted(orderId)
                ));
    }

    @Transactional(readOnly = true)
    public List<Order> list(
            String customerId,
            Instant startDate,
            Instant endDate,
            OrderStatus status,
            OrderSide orderSide,
            String assetName
    ) {
        if (startDate.isAfter(endDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCodes.INVALID_DATE_RANGE,
                    "startDate must not be after endDate"
            );
        }
        return orderRepository.findAll(
                OrderSpecifications.byCustomerAndRange(customerId, startDate, endDate, status, orderSide, assetName),
                Sort.by(Sort.Direction.DESC, "createDate").and(Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    private CreatedOrder replayIfPresent(String customerId, String idempotencyKey, String requestHash) {
        return idempotencyRecordRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey.trim())
                .map(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                ErrorCodes.IDEMPOTENCY_KEY_CONFLICT,
                                "Idempotency-Key was already used with a different request body"
                        );
                    }
                    return new CreatedOrder(getById(record.getOrderId()), true);
                })
                .orElse(null);
    }

    private void reserveBuy(String customerId, BigDecimal size, BigDecimal price) {
        Asset tryAsset = assetService.requireTry(customerId);
        BigDecimal requiredTry = Money.requiredTry(size, price);
        if (tryAsset.getUsableSize().compareTo(requiredTry) < 0) {
            throw insufficientBalance("Customer does not have sufficient usable TRY balance");
        }
        tryAsset.reserve(requiredTry);
    }

    private void reserveSell(String customerId, String assetName, BigDecimal size) {
        Asset stock = assetService.requireAsset(customerId, assetName);
        if (stock.getUsableSize().compareTo(size) < 0) {
            throw insufficientBalance("Customer does not have sufficient usable %s balance".formatted(assetName));
        }
        stock.reserve(size);
    }

    private void recordReservation(Order order) {
        if (order.getOrderSide() == OrderSide.BUY) {
            BigDecimal requiredTry = Money.requiredTry(order.getSize(), order.getPrice());
            ledgerService.record(
                    order.getCustomerId(),
                    Money.TRY,
                    BigDecimal.ZERO.setScale(Money.SIZE_SCALE),
                    requiredTry.negate(),
                    order.getId(),
                    LedgerReason.RESERVE
            );
        } else {
            ledgerService.record(
                    order.getCustomerId(),
                    order.getAssetName(),
                    BigDecimal.ZERO.setScale(Money.SIZE_SCALE),
                    order.getSize().negate(),
                    order.getId(),
                    LedgerReason.RESERVE
            );
        }
    }

    private void releaseReservation(Order order) {
        if (order.getOrderSide() == OrderSide.BUY) {
            Asset tryAsset = assetService.requireTry(order.getCustomerId());
            BigDecimal requiredTry = Money.requiredTry(order.getSize(), order.getPrice());
            tryAsset.release(requiredTry);
            ledgerService.record(
                    order.getCustomerId(),
                    Money.TRY,
                    BigDecimal.ZERO.setScale(Money.SIZE_SCALE),
                    requiredTry,
                    order.getId(),
                    LedgerReason.RELEASE
            );
        } else {
            Asset stock = assetService.requireAsset(order.getCustomerId(), order.getAssetName());
            stock.release(order.getSize());
            ledgerService.record(
                    order.getCustomerId(),
                    order.getAssetName(),
                    BigDecimal.ZERO.setScale(Money.SIZE_SCALE),
                    order.getSize(),
                    order.getId(),
                    LedgerReason.RELEASE
            );
        }
    }

    private String hashRequest(String customerId, String assetName, OrderSide side, BigDecimal size, BigDecimal price) {
        String canonical = customerId + "|" + assetName + "|" + side + "|" + size.toPlainString() + "|" + price.toPlainString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private ApiException insufficientBalance(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCodes.INSUFFICIENT_USABLE_BALANCE, message);
    }
}
