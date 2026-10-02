# AI governance

* AI **never** decides eligibility, amounts, or executes. It only reorders eligible options and writes explanations.
* Input is minimised: no name, customer ID or free text.
* Output must be JSON; non-JSON, unknown option types, or amounts outside the rule-approved set are rejected → rules-only fallback (`RULES_FALLBACK`).
* Agent classes have no dependency on consent/execution/repositories (enforced by a test).
* Requests marked `X-Actor-Type: AI_AGENT` receive 403 on approve/decline/execute endpoints.
* Consent is validated server-side at execution: customer actor, matching recommendation, unexpired (15 min).
* Recommendation source is always shown in the UI (`AI_DEMO_SCRIPTED`, `AI_LLM`, `RULES_FALLBACK`).
* No chain-of-thought is requested or stored; only concise business reasoning.