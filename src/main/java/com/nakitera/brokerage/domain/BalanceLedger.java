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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "balance_ledger",
        indexes = {
                @Index(name = "idx_ledger_customer_created", columnList = "customer_id, created_at"),
                @Index(name = "idx_ledger_order", columnList = "order_id")
        }
)
public class BalanceLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "asset_name", nullable = false, length = 32)
    private String assetName;

    @Column(name = "size_delta", nullable = false, precision = 19, scale = 6)
    private BigDecimal sizeDelta;

    @Column(name = "usable_delta", nullable = false, precision = 19, scale = 6)
    private BigDecimal usableDelta;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 16)
    private LedgerReason reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BalanceLedger() {
    }

    public BalanceLedger(
            String customerId,
            String assetName,
            BigDecimal sizeDelta,
            BigDecimal usableDelta,
            Long orderId,
            LedgerReason reason,
            Instant createdAt
    ) {
        this.customerId = customerId;
        this.assetName = assetName;
        this.sizeDelta = sizeDelta;
        this.usableDelta = usableDelta;
        this.orderId = orderId;
        this.reason = reason;
        this.createdAt = createdAt;
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

    public BigDecimal getSizeDelta() {
        return sizeDelta;
    }

    public BigDecimal getUsableDelta() {
        return usableDelta;
    }

    public Long getOrderId() {
        return orderId;
    }

    public LedgerReason getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
