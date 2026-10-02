package com.catalyst.customer;

import com.catalyst.api.NotFoundException;
import com.catalyst.domain.AccountEntity;
import com.catalyst.domain.AccountType;
import com.catalyst.domain.CustomerEntity;
import com.catalyst.infrastructure.persistence.Repositories.AccountRepository;
import com.catalyst.infrastructure.persistence.Repositories.CustomerRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class MockCustomerProfileClient implements CustomerProfileClient {
    private final CustomerRepository customers;
    private final AccountRepository accounts;

    public MockCustomerProfileClient(CustomerRepository customers, AccountRepository accounts) {
        this.customers = customers;
        this.accounts = accounts;
    }

    @Override
    public CustomerProfile fetch(String customerId) {
        CustomerEntity c = customers.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer " + customerId + " not found"));
        List<AccountEntity> accs = accounts.findByCustomerId(customerId);
        return new CustomerProfile(c.getId(), c.getName(), c.getAge(), c.getMonthlySalary(), c.getRiskProfile(),
                bal(accs, AccountType.CURRENT), bal(accs, AccountType.SAVINGS), c.getEmergencyFundTarget(),
                bal(accs, AccountType.INVESTMENT), bal(accs, AccountType.PERSONAL_LOAN),
                rate(accs, AccountType.PERSONAL_LOAN), c.getMonthlyLoanPayment());
    }

    private static BigDecimal bal(List<AccountEntity> a, AccountType t) {
        return a.stream().filter(x -> x.getType() == t).map(AccountEntity::getBalance).findFirst().orElse(BigDecimal.ZERO);
    }

    private static BigDecimal rate(List<AccountEntity> a, AccountType t) {
        return a.stream().filter(x -> x.getType() == t).map(AccountEntity::getInterestRate).findFirst().orElse(BigDecimal.ZERO);
    }
}
