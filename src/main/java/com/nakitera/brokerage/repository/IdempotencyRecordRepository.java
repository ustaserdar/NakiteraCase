package com.nakitera.brokerage.repository;

import com.nakitera.brokerage.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
}
