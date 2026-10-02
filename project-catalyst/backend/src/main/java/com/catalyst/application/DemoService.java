package com.catalyst.application;

import com.catalyst.api.Dtos.DemoStatus;
import com.catalyst.customer.BureauCache;
import com.catalyst.customer.ContextEnrichmentService;
import com.catalyst.demo.CatalystMetrics;
import com.catalyst.demo.CatalystProperties;
import com.catalyst.demo.DemoState;
import com.catalyst.domain.AccountType;
import com.catalyst.infrastructure.persistence.Repositories.AccountRepository;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DemoService {
    private static final String CUSTOMER = "CUST-1001";

    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final DemoState state;
    private final CatalystMetrics metrics;
    private final CatalystProperties props;
    private final BureauCache cache;
    private final ContextEnrichmentService enrichment;
    private final Environment env;

    public DemoService(JdbcTemplate jdbc, AccountRepository accounts, DemoState state, CatalystMetrics metrics,
                       CatalystProperties props, BureauCache cache, ContextEnrichmentService enrichment, Environment env) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.state = state;
        this.metrics = metrics;
        this.props = props;
        this.cache = cache;
        this.enrichment = enrichment;
        this.env = env;
    }

    @Transactional
    public void reset() {
        for (String t : List.of("audit_events", "consents", "financial_actions", "recommendations", "financial_context", "liquidity_events")) {
            jdbc.update("DELETE FROM " + t);
        }
        Map<AccountType, String> seed = Map.of(AccountType.CURRENT, "8500", AccountType.SAVINGS, "18000",
                AccountType.INVESTMENT, "35000", AccountType.CREDIT_CARD, "32000", AccountType.PERSONAL_LOAN, "95000");
        seed.forEach((type, bal) -> accounts.findByCustomerIdAndType(CUSTOMER, type).ifPresent(a -> {
            a.setBalance(new BigDecimal(bal));
            accounts.save(a);
        }));
        state.setAiDown(false);
        state.setCreditBureauDown(false);
        state.setComplianceBlock(false);
        cache.clear();
        enrichment.warmCache();
    }

    public void toggles(Boolean bureauDown, Boolean aiDown, Boolean complianceBlock) {
        if (complianceBlock != null) state.setComplianceBlock(complianceBlock);
        if (bureauDown != null) state.setCreditBureauDown(bureauDown);
        if (aiDown != null) state.setAiDown(aiDown);
    }

    public DemoStatus status() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String c : CatalystMetrics.COUNTERS) m.put(c, metrics.count(c));
        String aiSource = "llm".equalsIgnoreCase(props.aiMode()) ? "Spring AI (OpenAI / Azure OpenAI / Gemini / Ollama)" : "Deterministic demo mode (offline)";
        return new DemoStatus(props.bus(), props.aiMode(), aiSource, List.of(env.getActiveProfiles()),
                state.isCreditBureauDown(), state.isAiDown(), state.isComplianceBlock(), props.latencyTargetMs(), m,
                "AI recommends. Rules determine eligibility. The customer decides.");
    }
}
