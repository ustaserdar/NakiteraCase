package com.nakitera.brokerage.service;

import com.nakitera.brokerage.domain.BalanceLedger;
import com.nakitera.brokerage.domain.LedgerReason;
import com.nakitera.brokerage.repository.BalanceLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class LedgerService {

    private final BalanceLedgerRepository balanceLedgerRepository;
    private final AccessControlService accessControlService;
    private final Clock clock;

    public LedgerService(
            BalanceLedgerRepository balanceLedgerRepository,
            AccessControlService accessControlService,
            Clock clock
    ) {
        this.balanceLedgerRepository = balanceLedgerRepository;
        this.accessControlService = accessControlService;
        this.clock = clock;
    }

    public void record(
            String customerId,
            String assetName,
            BigDecimal sizeDelta,
            BigDecimal usableDelta,
            Long orderId,
            LedgerReason reason
    ) {
        balanceLedgerRepository.save(new BalanceLedger(
                customerId,
                assetName,
                sizeDelta,
                usableDelta,
                orderId,
                reason,
                Instant.now(clock)
        ));
    }

    @Transactional(readOnly = true)
    public List<BalanceLedger> listByCustomer(String customerId) {
        accessControlService.assertCanAccessCustomer(customerId);
        return balanceLedgerRepository.findByCustomerIdOrderByCreatedAtAscIdAsc(customerId);
    }
}
