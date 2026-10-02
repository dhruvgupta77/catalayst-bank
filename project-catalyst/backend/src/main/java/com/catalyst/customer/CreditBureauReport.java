package com.catalyst.customer;

import java.math.BigDecimal;

public record CreditBureauReport(String customerId, BigDecimal creditCardBalance, BigDecimal creditCardInterestRate,
                                 int creditScore) {
}
