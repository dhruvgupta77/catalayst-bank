package com.catalyst.customer;

import com.catalyst.demo.DemoState;
import com.catalyst.domain.AccountEntity;
import com.catalyst.domain.AccountType;
import com.catalyst.infrastructure.persistence.Repositories.AccountRepository;
import com.catalyst.infrastructure.persistence.Repositories.CustomerRepository;
import org.springframework.stereotype.Component;

@Component
public class MockCreditBureauClient implements CreditBureauClient {
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final DemoState demoState;

    public MockCreditBureauClient(AccountRepository accounts, CustomerRepository customers, DemoState demoState) {
        this.accounts = accounts;
        this.customers = customers;
        this.demoState = demoState;
    }

    @Override
    public CreditBureauReport fetch(String customerId) {
        if (demoState.isCreditBureauDown()) {
            throw new ExternalDependencyException("Credit bureau unavailable (simulated)");
        }
        AccountEntity card = accounts.findByCustomerIdAndType(customerId, AccountType.CREDIT_CARD)
                .orElseThrow(() -> new ExternalDependencyException("No bureau record for customer"));
        int score = customers.findById(customerId).map(c -> c.getCreditScore()).orElse(0);
        return new CreditBureauReport(customerId, card.getBalance(), card.getInterestRate(), score);
    }
}
