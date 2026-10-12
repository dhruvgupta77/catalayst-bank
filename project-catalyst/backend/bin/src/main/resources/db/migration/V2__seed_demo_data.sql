INSERT INTO customers (id, name, age, monthly_salary, risk_profile, monthly_loan_payment, emergency_fund_target, credit_score)
VALUES ('CUST-1001', 'Thabo Mokoena', 38, 42000.00, 'MODERATE', 4500.00, 50000.00, 742);

INSERT INTO accounts (id, customer_id, type, name, balance, interest_rate) VALUES
 ('ACC-1001-CUR', 'CUST-1001', 'CURRENT', 'Catalyst Current Account', 8500.00, 0),
 ('ACC-1001-SAV', 'CUST-1001', 'SAVINGS', 'Emergency Savings', 18000.00, 6.75),
 ('ACC-1001-INV', 'CUST-1001', 'INVESTMENT', 'Investment Account', 35000.00, 0),
 ('ACC-1001-CC',  'CUST-1001', 'CREDIT_CARD', 'Catalyst Credit Card', 32000.00, 22.00),
 ('ACC-1001-PL',  'CUST-1001', 'PERSONAL_LOAN', 'Personal Loan', 95000.00, 14.50);

INSERT INTO products (id, name, category, min_amount, max_amount, indicative_rate, risk_level, description) VALUES
 ('PRD-SAVINGS', 'Savings Account', 'SAVINGS', 0, 5000000, 6.75, 0, 'Instant-access savings account'),
 ('PRD-FIXED', 'Fixed Deposit', 'INVESTMENT', 5000, 5000000, 8.10, 0, '12-month fixed deposit, funds locked in'),
 ('PRD-MONEY-MKT', 'Money Market', 'INVESTMENT', 1000, 5000000, 7.40, 1, 'Low-risk money market fund'),
 ('PRD-INVEST', 'Investment Account', 'INVESTMENT', 1000, 5000000, 0, 2, 'Diversified unit-trust investment account, returns not guaranteed'),
 ('PRD-DEBT', 'Debt Repayment', 'DEBT', 100, 5000000, 0, 0, 'Additional repayment towards an existing debt');
