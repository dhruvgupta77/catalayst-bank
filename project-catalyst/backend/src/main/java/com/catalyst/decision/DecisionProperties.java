package com.catalyst.decision;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "catalyst.decision")
public record DecisionProperties(BigDecimal highInterestThreshold, BigDecimal minAccountBuffer,
                                 BigDecimal transactionLimit, BigDecimal salaryDiscretionaryShare,
                                 BigDecimal insuranceAllocatableShare, BigDecimal minEmergencyCoverageToInvest) {
    public DecisionProperties {
        if (highInterestThreshold == null) highInterestThreshold = new BigDecimal("15");
        if (minAccountBuffer == null) minAccountBuffer = new BigDecimal("2000");
        if (transactionLimit == null) transactionLimit = new BigDecimal("100000");
        if (salaryDiscretionaryShare == null) salaryDiscretionaryShare = new BigDecimal("0.15");
        if (insuranceAllocatableShare == null) insuranceAllocatableShare = new BigDecimal("0.50");
        if (minEmergencyCoverageToInvest == null) minEmergencyCoverageToInvest = new BigDecimal("0.25");
    }
}
