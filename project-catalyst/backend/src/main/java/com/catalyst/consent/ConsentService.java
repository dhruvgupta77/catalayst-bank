package com.catalyst.consent;

import com.catalyst.demo.CatalystProperties;
import com.catalyst.domain.ConsentEntity;
import com.catalyst.domain.FinancialActionEntity;
import com.catalyst.infrastructure.persistence.Repositories.ConsentRepository;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Only the customer-facing approval flow may grant consent; execution refuses to run without it.
 */
@Service
public class ConsentService {
    public static final String CUSTOMER = "CUSTOMER";

    private final ConsentRepository repo;
    private final CatalystProperties props;

    public ConsentService(ConsentRepository repo, CatalystProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public ConsentEntity grant(FinancialActionEntity action, String channel) {
        Instant now = Instant.now();
        ConsentEntity c = new ConsentEntity();
        c.setRecommendationId(action.getRecommendationId());
        c.setActionId(action.getId());
        c.setCustomerId(action.getCustomerId());
        c.setActor(CUSTOMER);
        c.setChannel(channel);
        c.setGrantedAt(now);
        c.setExpiresAt(now.plus(Duration.ofMinutes(props.consentTtlMinutes())));
        c.setStatement(String.format(Locale.US, "Customer approved moving R%,.0f: %s. Approval is voluntary and can be declined.",
                action.getAmount().setScale(0, RoundingMode.HALF_UP), action.getProductName()));
        return repo.save(c);
    }

    public void requireValid(FinancialActionEntity action) {
        Instant now = Instant.now();
        boolean ok = repo.findByActionId(action.getId()).stream().anyMatch(c ->
                CUSTOMER.equals(c.getActor()) && c.getCustomerId().equals(action.getCustomerId())
                        && c.getRecommendationId().equals(action.getRecommendationId()) && c.getExpiresAt().isAfter(now));
        if (!ok) throw new ConsentRequiredException("No valid customer consent for action " + action.getId());
    }
}
