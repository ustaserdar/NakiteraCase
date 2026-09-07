package com.nakitera.brokerage.repository;

import com.nakitera.brokerage.domain.BalanceLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BalanceLedgerRepository extends JpaRepository<BalanceLedger, Long> {

    List<BalanceLedger> findByCustomerIdOrderByCreatedAtAscIdAsc(String customerId);
}
