package com.catalyst.decision;

import com.catalyst.domain.ActionType;

import java.math.BigDecimal;

public record ActionProposal(ActionType type, BigDecimal amount, int priority, String reason,
                             String productId, String productName, String impact) {
}
