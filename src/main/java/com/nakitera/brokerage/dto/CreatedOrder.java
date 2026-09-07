package com.nakitera.brokerage.dto;

import com.nakitera.brokerage.domain.Order;

public record CreatedOrder(Order order, boolean replayed) {
}
