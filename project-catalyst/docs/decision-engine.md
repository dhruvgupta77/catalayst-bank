# Decision engine

Input: LiquidityEvent + CustomerProfile + FinancialContext + ProductCatalog → `DecisionResult` (ordered actions + rule results).

Policy (configurable in `catalyst.decision.*`):
* Allocatable share: Salary 15 %, Insurance 50 %, others 100 %.
* Debt repayment (weight 0.45) only if card rate ≥ 15 %.
* Emergency savings (0.35) only if below target.
* Investment (0.20) only if emergency coverage ≥ 25 % and a product fits the risk profile.
* Amounts floor to R1,000 steps (R100 for small inflows); residue goes to the lowest-priority option with headroom.
* Guardrails: R2,000 minimum current-account buffer, R100,000 transaction limit, consent required.

Worked example — Two-Pot R45,000 → debt R20,000, savings R15,000, investment R10,000.
Salary R42,000 → allocatable R6,300 → R2,800 / R2,200 / R1,300 (same engine, different context).