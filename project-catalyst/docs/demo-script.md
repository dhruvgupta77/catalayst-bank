# Demo script

**Key message:** Two-Pot isn't the product. Liquidity events are the product opportunity.

## Demo 1 — Two-Pot (≈90 s)
1. (0:00) Open Dashboard — Thabo, R8,500, card debt R32k @ 22 %, emergency fund 36 %.
2. (0:10) Simulator → **Two-Pot** → R45,000. Say: "A decision window opens the moment money lands."
3. (0:20) Presenter view: Demo Processing Timeline (received → context → rules → recommendation → app updated).
4. (0:35) Recommendation: "Here are some options you may want to consider" — debt R20k, savings R15k, invest R10k. Point at the *Rules* vs *AI* split.
5. (0:55) Review → tick confirmation → **Approve**. "The customer decides."
6. (1:10) Execution progress, balances change.
7. (1:20) Audit timeline: EVENT_RECEIVED … ACTION_EXECUTED.

## Demo 2 — Salary (≈30 s)
Reset, trigger Salary R42,000. Same pipeline, allocatable R6,300 → different plan. "Same architecture, different event."

## Optional resilience (60 s)
Bureau down → cached context; AI down → rules fallback; Replay → duplicate blocked; Decline → nothing executes.

## Talking points
* AI recommends; rules determine eligibility; customer decides.
* Pattern: Detect → Understand → Decide → Recommend → Consent → Act → Audit.
* Timeline is a *demo* timeline, not a production latency claim.
* Swap providers via interfaces: bureau, profile, catalog, LLM.