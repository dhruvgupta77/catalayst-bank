package com.catalyst.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.catalyst.demo.CatalystProperties;
import com.catalyst.domain.ActionType;
import com.catalyst.domain.ConsentEntity;
import com.catalyst.domain.FinancialActionEntity;
import com.catalyst.infrastructure.persistence.Repositories.ConsentRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConsentServiceTest {
    private final ConsentRepository repo = mock(ConsentRepository.class);
    private final ConsentService service = new ConsentService(repo, new CatalystProperties("memory", "scripted", 2000, 0, 15));

    private FinancialActionEntity action() {
        FinancialActionEntity a = new FinancialActionEntity();
        a.setId("ACT-1");
        a.setRecommendationId("REC-1");
        a.setCustomerId("CUST-1001");
        a.setActionType(ActionType.SAVINGS);
        a.setAmount(new BigDecimal("15000"));
        a.setProductName("Savings Account");
        return a;
    }

    private ConsentEntity consent(String actor, String customer, Instant expires) {
        ConsentEntity c = new ConsentEntity();
        c.setActor(actor);
        c.setCustomerId(customer);
        c.setRecommendationId("REC-1");
        c.setActionId("ACT-1");
        c.setExpiresAt(expires);
        return c;
    }

    @Test
    void rejectsWhenNoConsentExists() {
        when(repo.findByActionId("ACT-1")).thenReturn(List.of());
        assertThatThrownBy(() -> service.requireValid(action())).isInstanceOf(ConsentRequiredException.class);
    }

    @Test
    void rejectsExpiredConsent() {
        when(repo.findByActionId("ACT-1")).thenReturn(List.of(consent("CUSTOMER", "CUST-1001", Instant.now().minusSeconds(5))));
        assertThatThrownBy(() -> service.requireValid(action())).isInstanceOf(ConsentRequiredException.class);
    }

    @Test
    void rejectsConsentNotGrantedByCustomer() {
        when(repo.findByActionId("ACT-1")).thenReturn(List.of(consent("AI_AGENT", "CUST-1001", Instant.now().plusSeconds(60))));
        assertThatThrownBy(() -> service.requireValid(action())).isInstanceOf(ConsentRequiredException.class);
    }

    @Test
    void rejectsConsentForAnotherCustomer() {
        when(repo.findByActionId("ACT-1")).thenReturn(List.of(consent("CUSTOMER", "CUST-9999", Instant.now().plusSeconds(60))));
        assertThatThrownBy(() -> service.requireValid(action())).isInstanceOf(ConsentRequiredException.class);
    }

    @Test
    void acceptsValidCustomerConsent() {
        when(repo.findByActionId("ACT-1")).thenReturn(List.of(consent("CUSTOMER", "CUST-1001", Instant.now().plusSeconds(60))));
        service.requireValid(action());
    }

    @Test
    void grantRecordsCustomerActorAndExpiry() {
        when(repo.save(any(ConsentEntity.class))).thenAnswer(i -> i.getArgument(0));
        ConsentEntity c = service.grant(action(), "CONFIRMATION_SCREEN");
        assertThat(c.getActor()).isEqualTo("CUSTOMER");
        assertThat(c.getExpiresAt()).isAfter(Instant.now().plusSeconds(800));
        assertThat(c.getStatement()).contains("R15,000");
    }
}