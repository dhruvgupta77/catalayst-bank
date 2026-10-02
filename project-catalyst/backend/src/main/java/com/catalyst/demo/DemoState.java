package com.catalyst.demo;

import org.springframework.stereotype.Component;

/**
 * Presenter-controlled failure toggles.
 */
@Component
public class DemoState {
    private volatile boolean creditBureauDown;
    private volatile boolean aiDown;
    private volatile boolean complianceBlock;

    public boolean isComplianceBlock() {
        return complianceBlock;
    }

    public void setComplianceBlock(boolean v) {
        this.complianceBlock = v;
    }

    public boolean isCreditBureauDown() {
        return creditBureauDown;
    }

    public boolean isAiDown() {
        return aiDown;
    }

    public void setCreditBureauDown(boolean v) {
        this.creditBureauDown = v;
    }

    public void setAiDown(boolean v) {
        this.aiDown = v;
    }
}
