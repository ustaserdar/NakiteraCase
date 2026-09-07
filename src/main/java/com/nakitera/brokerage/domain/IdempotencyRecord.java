package com.nakitera.brokerage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_idempotency_customer_key",
                columnNames = {"customer_id", "idempotency_key"}
        )
)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String customerId, String idempotencyKey, String requestHash, Long orderId) {
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.orderId = orderId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getOrderId() {
        return orderId;
    }
}
