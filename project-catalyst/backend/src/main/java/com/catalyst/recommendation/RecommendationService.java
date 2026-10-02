package com.catalyst.recommendation;

import com.catalyst.decision.ActionProposal;
import com.catalyst.decision.DecisionResult;
import com.catalyst.decision.FinancialContext;
import com.catalyst.decision.LiquidityEvent;
import com.catalyst.demo.CatalystProperties;
import com.catalyst.demo.DemoState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Combines rules + AI. The AI can only re-rank / explain; if it fails or misbehaves we fall back to rules-only.
 */
@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationAgent primary;
    private final RecommendationAgent fallback = new RulesOnlyAgent();
    private final String primarySource;
    private final DemoState demoState;

    public RecommendationService(LlmClient llm, ObjectMapper mapper, CatalystProperties props, DemoState demoState) {
        boolean useLlm = "llm".equalsIgnoreCase(props.aiMode()) && llm.isAvailable();
        this.primary = useLlm ? new LlmRecommendationAgent(llm, mapper) : new DemoScriptedAgent();
        this.primarySource = useLlm ? "AI_LLM" : "AI_DEMO_SCRIPTED";
        this.demoState = demoState;
    }

    public RecommendationOutcome recommend(LiquidityEvent event, FinancialContext ctx, DecisionResult decision) {
        AgentInput input = toInput(event, ctx, decision);
        AiRecommendation narrative;
        String source = primarySource;
        String fallbackReason = null;
        try {
            if (demoState.isAiDown()) throw new AgentException("AI provider unavailable (simulated)");
            narrative = primary.recommend(input);
        } catch (AgentException e) {
            log.warn("AI path rejected for event {}: {} - falling back to rules-only", event.eventId(), e.getMessage());
            narrative = fallback.recommend(input);
            source = "RULES_FALLBACK";
            fallbackReason = e.getMessage();
        }
        return new RecommendationOutcome(applyRanking(decision.recommendedActions(), narrative.rankedOptions()),
                narrative, source, fallbackReason);
    }

    AgentInput toInput(LiquidityEvent event, FinancialContext ctx, DecisionResult d) {
        Integer coverage = ctx.emergencyTarget().signum() == 0 ? null
                : ctx.emergencySavings().multiply(new BigDecimal("100")).divide(ctx.emergencyTarget(), 0, RoundingMode.HALF_UP).intValue();
        return new AgentInput(event.eventType().label(), d.eventAmount(), d.keptInAccount(), d.recommendedActions(),
                ctx.riskProfile().name(), coverage, ctx.creditCardBalance(), ctx.creditCardRate(), ctx.emergencySavings(), ctx.emergencyTarget(),
                ctx.reducedContext());
    }

    /**
     * Re-orders only; amounts and membership stay exactly as decided by the rules.
     */
    static List<ActionProposal> applyRanking(List<ActionProposal> actions, List<String> ranking) {
        List<ActionProposal> ordered = new ArrayList<>();
        if (ranking != null) {
            for (String r : ranking) {
                actions.stream().filter(a -> a.type().name().equals(r) && !ordered.contains(a)).findFirst().ifPresent(ordered::add);
            }
        }
        actions.stream().filter(a -> !ordered.contains(a)).forEach(ordered::add);
        List<ActionProposal> result = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            ActionProposal a = ordered.get(i);
            result.add(new ActionProposal(a.type(), a.amount(), i + 1, a.reason(), a.productId(), a.productName(), a.impact()));
        }
        return result;
    }
}

