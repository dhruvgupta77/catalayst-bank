package com.catalyst.decision;

/**
 * Result of one deterministic rule, surfaced in the audit trail and UI.
 */
public record RuleOutcome(String ruleId, boolean passed, String detail) {
}
