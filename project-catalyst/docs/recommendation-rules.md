# Recommendation Rules — Summary

All eligibility and amounts come from the **deterministic `DecisionEngine`**. AI may only re-order and explain the resulting options; it can never add, resize or execute them. Every action needs explicit customer approval.

Source: `backend/src/main/java/com/catalyst/decision/`. Thresholds are configurable under `catalyst.decision.*` in `application.yml`.

## 1. Evaluation order

1. **Event policy** — how much of the inflow is available for guidance.
2. **Option rules** — Debt, Emergency savings, Investment: each decides *eligible or not*.
3. **Allocation** — split the available amount by weight.
4. **Guardrails** — product minimums, account buffer, transaction limit.
5. **Consent** — always required (`CONSENT_REQUIRED`).

Each step is recorded as a rule outcome (`ruleId`, passed, detail) and shown in the UI and audit trail.

## 2. Event policy (available share of the inflow)

| Event | Share used for guidance |
|---|---|
| Salary | 15% (`salary-discretionary-share`) |
| Insurance payout | 50% (`insurance-allocatable-share`) |
| Bonus, Two-Pot, Tax refund, other | 100% |

The remainder stays in the current account.

## 3. Option rules

| Option | Rule ID | Eligible when | Weight | Cap | Product |
|---|---|---|---|---|---|
| Debt repayment | `HIGH_INTEREST_DEBT` | Credit card balance > 0 **and** rate ≥ 15% p.a. (`high-interest-threshold`) | 0.45 | Outstanding card balance | Debt Repayment |
| Emergency savings | `EMERGENCY_FUND_GAP` | Emergency savings < emergency target | 0.35 | Gap to target | Savings Account |
| Investment | `INVESTMENT_SUITABILITY` | Emergency coverage ≥ 25% of target (`min-emergency-coverage-to-invest`) **and** a catalog investment product has risk level ≤ customer's risk profile | 0.20 | Product max amount | Highest-risk/-rate product within the customer's risk level |

If credit-bureau data is missing, the debt option is not offered. When the bureau is down, cached values are used and the context is flagged as **reduced** (the rule detail says "based on last known balance").

## 4. Allocation

- Available amount = inflow × event share (rounded down).
- Eligible options are sorted by weight (this gives priority 1, 2, 3).
- Each option gets `available × weight ÷ total eligible weight`, so weights are renormalised when an option is ineligible.
- Amounts round **down** to R1,000 steps when the available amount ≥ R10,000, otherwise R100 steps.
- Each amount is limited by its cap and the transaction limit.
- Rounding residue goes to the lowest-priority option that still has headroom.

## 5. Guardrails

| Rule ID | Behaviour | Default |
|---|---|---|
| `PRODUCT_MINIMUM` | Option dropped if the amount is below the product's minimum | Per product |
| `MIN_ACCOUNT_BUFFER` | Options are trimmed (lowest priority first) so the current account keeps at least this amount after the actions | R2,000 |
| `TRANSACTION_LIMIT` | Cap on any single action | R100,000 |
| `CONSENT_REQUIRED` | Nothing executes without customer approval; consent expires after 15 minutes | always |

## 6. Worked examples (Thabo: balance R8,500, card R32,000 @ 22%, emergency R18,000 / R50,000, Moderate risk)

| Scenario | Available | Debt | Savings | Investment |
|---|---|---|---|---|
| Two-Pot R45,000 | R45,000 | R20,000 | R15,000 | R10,000 |
| Salary R42,000 | R6,300 | R2,800 | R2,200 | R1,300 |

The same engine gives different outputs because the event policy and customer context differ. Emergency coverage is 36%, so the investment rule passes.

## 7. What the AI layer may and may not do

- **May:** re-rank the eligible options and write plain-language explanations.
- **May not:** add options, change amounts, override rules, or call approve/execute.
- Output is validated: non-JSON, unknown option types, or amounts outside the rule-approved set are rejected, and the system falls back to `RULES_FALLBACK` (rule order, no AI narrative).

See also [decision-engine.md](decision-engine.md) and [ai-governance.md](ai-governance.md).
