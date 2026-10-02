# Failure scenarios (Presenter screen toggles)

| Scenario | How | Expected result |
|---|---|---|
| Credit bureau down | Toggle, then trigger event | "External dependency unavailable — using cached customer context"; reduced-context recommendation, lower confidence |
| AI unavailable | Toggle, then trigger event | Rules-only fallback recommendation, source `RULES_FALLBACK` |
| Duplicate event | "Replay event" | "Duplicate event detected"; no second action; audit `DUPLICATE_EVENT_DETECTED` |
| Customer declines | Decline | "Recommendation declined — no financial action executed" |
| Execute without consent | API call | 403 + `ACTION_REJECTED` audit |

`POST /api/demo/reset` restores seed data between runs.
| Compliance rule fails | Presenter toggle `Compliance rule fails`, accept | Action FAILED, ACTION_BLOCKED audited, no money moved |
| Customer declines | Decline on Story screen | NO_ACTION_EXECUTED audited, all actions cancelled |
| Modify above limit | Modify to more than proposed | HTTP 422, POLICY_BLOCKED audited |
| Missing consent | Execute without approval | HTTP 403, ACTION_REJECTED audited |
