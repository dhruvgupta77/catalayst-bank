package com.catalyst.decision;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Money {
    private Money() {
    }

    static String rand(BigDecimal v) {
        return "R" + String.format(java.util.Locale.US, "%,.0f", v.setScale(0, RoundingMode.HALF_UP));
    }

    static BigDecimal floorTo(BigDecimal v, BigDecimal step) {
        if (v.signum() <= 0) return BigDecimal.ZERO;
        return v.divideToIntegralValue(step).multiply(step);
    }
}
