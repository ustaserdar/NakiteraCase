package com.nakitera.brokerage.repository;

import com.nakitera.brokerage.domain.Order;
import com.nakitera.brokerage.domain.OrderSide;
import com.nakitera.brokerage.domain.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> byCustomerAndRange(
            String customerId,
            Instant startDate,
            Instant endDate,
            OrderStatus status,
            OrderSide orderSide,
            String assetName
    ) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("createDate"), startDate));
            predicates.add(cb.lessThanOrEqualTo(root.get("createDate"), endDate));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (orderSide != null) {
                predicates.add(cb.equal(root.get("orderSide"), orderSide));
            }
            if (assetName != null && !assetName.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("assetName")), assetName.toLowerCase()));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
