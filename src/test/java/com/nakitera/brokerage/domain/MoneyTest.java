package com.nakitera.brokerage.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void requiredTryUsesExactDecimalArithmetic() {
        BigDecimal required = Money.requiredTry(new BigDecimal("10"), new BigDecimal("250.50"));
        assertThat(required).isEqualByComparingTo("2505.000000");
        assertThat(Money.isTry("try")).isTrue();
        assertThat(Money.isTry("THYAO")).isFalse();
    }
}
