package com.catalyst.decision;

import java.math.BigDecimal;

public record Product(String id, String name, String category, BigDecimal minAmount, BigDecimal maxAmount,
                      BigDecimal indicativeRate, int riskLevel, String description) {
}
