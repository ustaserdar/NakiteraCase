package com.nakitera.brokerage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "orders",
        indexes = @Index(name = "idx_orders_customer_created", columnList = "customer_id, create_date")
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "asset_name", nullable = false, length = 32)
    private String assetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_side", nullable = false, length = 8)
    private OrderSide orderSide;

    @Column(name = "size", nullable = false, precision = 19, scale = 6)
    private BigDecimal size;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "create_date", nullable = false)
    private Instant createDate;

    @Version
    private Long version;

    protected Order() {
    }

    public Order(
            String customerId,
            String assetName,
            OrderSide orderSide,
            BigDecimal size,
            BigDecimal price,
            OrderStatus status,
            Instant createDate
    ) {
        this.customerId = customerId;
        this.assetName = assetName;
        this.orderSide = orderSide;
        this.size = size;
        this.price = price;
        this.status = status;
        this.createDate = createDate;
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    public void markCanceled() {
        this.status = OrderStatus.CANCELED;
    }

    public void markMatched() {
        this.status = OrderStatus.MATCHED;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getAssetName() {
        return assetName;
    }

    public OrderSide getOrderSide() {
        return orderSide;
    }

    public BigDecimal getSize() {
        return size;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreateDate() {
        return createDate;
    }

    public Long getVersion() {
        return version;
    }
}
