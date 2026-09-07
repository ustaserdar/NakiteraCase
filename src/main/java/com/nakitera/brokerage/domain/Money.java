package com.nakitera.brokerage.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final String TRY = "TRY";
    public static final int SIZE_SCALE = 6;
    public static final int PRICE_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Money() {
    }

    public static boolean isTry(String assetName) {
        return assetName != null && TRY.equalsIgnoreCase(assetName.trim());
    }

    public static BigDecimal requiredTry(BigDecimal size, BigDecimal price) {
        return size.multiply(price).setScale(SIZE_SCALE, ROUNDING);
    }

    public static BigDecimal normalizeSize(BigDecimal value) {
        return value.setScale(SIZE_SCALE, ROUNDING);
    }

    public static BigDecimal normalizePrice(BigDecimal value) {
        return value.setScale(PRICE_SCALE, ROUNDING);
    }
}
