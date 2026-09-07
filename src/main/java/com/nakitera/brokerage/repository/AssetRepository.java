package com.nakitera.brokerage.repository;

import com.nakitera.brokerage.domain.Asset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByCustomerIdAndAssetName(String customerId, String assetName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Asset a where a.customerId = :customerId and a.assetName = :assetName")
    Optional<Asset> findByCustomerIdAndAssetNameForUpdate(
            @Param("customerId") String customerId,
            @Param("assetName") String assetName
    );

    List<Asset> findByCustomerIdOrderByAssetNameAsc(String customerId);
}
