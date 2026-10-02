package com.catalyst.event;

public final class Topics {
    private Topics() {
    }

    public static final String LIQUIDITY_EVENTS = "liquidity-events";
    public static final String CUSTOMER_CONTEXT = "customer-context";
    public static final String RECOMMENDATIONS = "recommendations";
    public static final String FINANCIAL_ACTIONS = "financial-actions";
    public static final String AUDIT_EVENTS = "audit-events";
    public static final String[] ALL = {LIQUIDITY_EVENTS, CUSTOMER_CONTEXT, RECOMMENDATIONS, FINANCIAL_ACTIONS, AUDIT_EVENTS};
}
