package com.catalyst.domain;

public enum RiskProfile {
    CONSERVATIVE(1), MODERATE(2), AGGRESSIVE(3);

    private final int level;

    RiskProfile(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }
}

