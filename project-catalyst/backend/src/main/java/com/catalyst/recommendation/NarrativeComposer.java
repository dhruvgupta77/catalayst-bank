package com.catalyst.recommendation;

import com.catalyst.decision.ActionProposal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Template-based narrative: used by the offline demo agent and as the rules-only fallback.
 */
final class NarrativeComposer {
    private NarrativeComposer() {
    }

    static String rand(BigDecimal v) {
        return "R" + String.format(Locale.US, "%,.0f", v.setScale(0, RoundingMode.HALF_UP));
    }

    static String describe(ActionProposal a) {
        return switch (a.type()) {
            case DEBT_REPAYMENT -> "pay down high-interest debt";
            case SAVINGS -> "build your emergency savings";
            case INVESTMENT -> "invest for the long term";
        };
    }

    static AiRecommendation compose(AgentInput in, Double confidence, boolean rich) {
        List<ActionProposal> opts = in.options();
        int n = opts.size();
        if (n == 0) {
            return new AiRecommendation("You received " + rand(in.eventAmount()) + ".",
                    "No options meet the rules for this amount right now; keeping it in your account is fine.",
                    List.of("The bank\u2019s rules found no eligible options for your current situation."),
                    List.of("Keep the full amount in your current account."), confidence, List.of());
        }
        String summary = "You received " + rand(in.eventAmount()) + " (" + in.eventLabel() + "). We found " + n
                + (n == 1 ? " option" : " options") + " you may want to consider.";
        StringBuilder rec = new StringBuilder("Here is one way to put this money to work: ");
        for (int i = 0; i < n; i++) {
            ActionProposal a = opts.get(i);
            rec.append(i == 0 ? "" : i == n - 1 ? ", and " : ", ").append(describe(a)).append(" (").append(rand(a.amount())).append(")");
        }
        rec.append(". You can accept, change or decline any of these.");
        List<String> reasoning = new ArrayList<>();
        for (ActionProposal a : opts) {
            reasoning.add(rich ? a.reason() + ": " + a.impact() + "." : a.reason() + ".");
        }
        if (in.reducedContext()) {
            reasoning.add("Some external data was unavailable, so this uses last-known information and may be less precise.");
        }
        List<String> alternatives = new ArrayList<>();
        if (in.keptInAccount().signum() > 0) {
            alternatives.add("Keep " + rand(in.keptInAccount()) + " in your current account for flexibility.");
        }
        alternatives.add("Decline all options and decide later; nothing happens without your approval.");
        return new AiRecommendation(summary, rec.toString(), reasoning, alternatives, confidence,
                opts.stream().map(o -> o.type().name()).toList());
    }
}
