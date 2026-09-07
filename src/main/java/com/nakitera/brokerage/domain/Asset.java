package com.nakitera.brokerage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

@Entity
@Table(
        name = "assets",
        uniqueConstraints = @UniqueConstraint(name = "uk_asset_customer_name", columnNames = {"customer_id", "asset_name"})
)
@Check(constraints = "size >= 0 AND usable_size >= 0 AND usable_size <= size")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "asset_name", nullable = false, length = 32)
    private String assetName;

    @Column(name = "size", nullable = false, precision = 19, scale = 6)
    private BigDecimal size;

    @Column(name = "usable_size", nullable = false, precision = 19, scale = 6)
    private BigDecimal usableSize;

    @Version
    private Long version;

    protected Asset() {
    }

    public Asset(String customerId, String assetName, BigDecimal size, BigDecimal usableSize) {
        this.customerId = customerId;
        this.assetName = assetName;
        this.size = size;
        this.usableSize = usableSize;
    }

    public void reserve(BigDecimal amount) {
        this.usableSize = this.usableSize.subtract(amount);
    }

    public void release(BigDecimal amount) {
        this.usableSize = this.usableSize.add(amount);
    }

    public void reduceTotal(BigDecimal amount) {
        this.size = this.size.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.size = this.size.add(amount);
        this.usableSize = this.usableSize.add(amount);
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

    public BigDecimal getSize() {
        return size;
    }

    public BigDecimal getUsableSize() {
        return usableSize;
    }

    public Long getVersion() {
        return version;
    }
}
