package com.catalyst.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Event -> Recommendation -> Consent -> Execution -> Audit, offline (H2 + in-memory bus + deterministic AI).
 */
@SpringBootTest(properties = "catalyst.execution-step-delay-ms=0")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoFlowIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    private JsonNode json(String method, String url, String body) throws Exception {
        var req = method.equals("GET") ? get(url) : post(url);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mapper.readTree(mvc.perform(req).andReturn().getResponse().getContentAsString());
    }

    private int statusOf(String url, String body, String... headers) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(body == null ? "{}" : body);
        for (int i = 0; i < headers.length; i += 2) req.header(headers[i], headers[i + 1]);
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    private JsonNode trigger(String scenario, String key) throws Exception {
        String body = "{\"scenario\":\"" + scenario + "\"" + (key == null ? "" : ",\"idempotencyKey\":\"" + key + "\"") + "}";
        return json("POST", "/api/demo/liquidity-events", body);
    }

    private JsonNode awaitRecommendation(String eventId) {
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            JsonNode e = json("GET", "/api/liquidity-events/" + eventId, null);
            return e.hasNonNull("recommendationId");
        });
        try {
            return json("GET", "/api/recommendations/" + json("GET", "/api/liquidity-events/" + eventId, null).get("recommendationId").asText(), null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> auditActions(String eventId) throws Exception {
        return StreamSupport.stream(json("GET", "/api/audit/" + eventId, null).get("entries").spliterator(), false)
                .map(n -> n.get("action").asText()).toList();
    }

    private JsonNode customer() throws Exception {
        return json("GET", "/api/customers/CUST-1001", null);
    }

    private double balance(String type) throws Exception {
        for (JsonNode a : customer().get("accounts"))
            if (a.get("type").asText().equals(type)) return a.get("balance").asDouble();
        throw new IllegalStateException(type);
    }

    @Test
    void twoPotEndToEndRequiresConsentThenExecutesAndAudits() throws Exception {
        String eventId = trigger("TWO_POT_PAYOUT", null).get("eventId").asText();
        JsonNode reco = awaitRecommendation(eventId);
        String recoId = reco.get("id").asText();

        assertThat(reco.get("source").asText()).isEqualTo("AI_DEMO_SCRIPTED");
        assertThat(reco.get("actions")).hasSize(3);
        assertThat(reco.get("actions").get(0).get("type").asText()).isEqualTo("DEBT_REPAYMENT");
        assertThat(reco.get("actions").get(0).get("amount").asInt()).isEqualTo(20000);
        assertThat(reco.get("actions").get(1).get("amount").asInt()).isEqualTo(15000);
        assertThat(reco.get("actions").get(2).get("amount").asInt()).isEqualTo(10000);
        assertThat(balance("CURRENT")).isEqualTo(53500.0);

        // No consent => execution is refused and no money moves.
        String firstAction = reco.get("actions").get(0).get("id").asText();
        assertThat(statusOf("/api/actions/" + firstAction + "/execute", null)).isEqualTo(403);
        assertThat(balance("CREDIT_CARD")).isEqualTo(32000.0);

        // Approval without explicit confirmation is refused; AI callers are blocked.
        assertThat(statusOf("/api/recommendations/" + recoId + "/approve",
                "{\"actionIds\":[\"" + firstAction + "\"],\"confirmed\":false}")).isEqualTo(400);
        assertThat(statusOf("/api/recommendations/" + recoId + "/approve",
                "{\"actionIds\":[\"" + firstAction + "\"],\"confirmed\":true}", "X-Actor-Type", "AI_AGENT")).isEqualTo(403);
        assertThat(balance("CREDIT_CARD")).isEqualTo(32000.0);

        json("POST", "/api/recommendations/" + recoId + "/viewed", "{}");
        List<String> ids = StreamSupport.stream(reco.get("actions").spliterator(), false).map(a -> a.get("id").asText()).toList();
        String approveBody = "{\"confirmed\":true,\"actionIds\":[\"" + String.join("\",\"", ids) + "\"]}";
        assertThat(statusOf("/api/recommendations/" + recoId + "/approve", approveBody)).isEqualTo(200);

        await().atMost(Duration.ofSeconds(10)).until(() -> StreamSupport.stream(
                        json("GET", "/api/recommendations/" + recoId, null).get("actions").spliterator(), false)
                .allMatch(a -> a.get("status").asText().equals("EXECUTED")));

        assertThat(balance("CREDIT_CARD")).isEqualTo(12000.0);
        assertThat(balance("SAVINGS")).isEqualTo(33000.0);
        assertThat(balance("INVESTMENT")).isEqualTo(45000.0);
        assertThat(balance("CURRENT")).isEqualTo(8500.0);

        // Re-approving is idempotent: nothing is executed twice.
        assertThat(statusOf("/api/recommendations/" + recoId + "/approve", approveBody)).isEqualTo(200);
        assertThat(balance("CREDIT_CARD")).isEqualTo(12000.0);

        List<String> audit = auditActions(eventId);
        assertThat(audit).containsSubsequence("EVENT_RECEIVED", "CONTEXT_ENRICHED", "RULES_EVALUATED",
                "RECOMMENDATION_GENERATED", "RECOMMENDATION_PUBLISHED", "CUSTOMER_VIEWED", "CUSTOMER_APPROVED", "ACTION_EXECUTED");
        assertThat(audit).contains("ACTION_REJECTED");
        assertThat(audit.stream().filter(a -> a.equals("ACTION_EXECUTED")).count()).isEqualTo(3);

        JsonNode status = json("GET", "/api/demo/status", null);
        assertThat(status.get("metrics").get("actions.executed").asDouble()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void salaryProducesADifferentRecommendationFromTheSamePipeline() throws Exception {
        JsonNode reco = awaitRecommendation(trigger("SALARY", null).get("eventId").asText());
        assertThat(reco.get("event").get("type").asText()).isEqualTo("SALARY");
        assertThat(reco.get("allocatable").asInt()).isEqualTo(6300);
        assertThat(reco.get("actions").get(0).get("amount").asInt()).isNotEqualTo(20000);
        assertThat(reco.get("narrative").get("summary").asText()).contains("R42,000");
    }

    @Test
    void duplicateEventsAreDetectedAndNothingIsExecutedTwice() throws Exception {
        JsonNode first = trigger("BONUS", "dup-key-1");
        String eventId = first.get("eventId").asText();
        JsonNode reco = awaitRecommendation(eventId);
        double balanceAfterFirst = balance("CURRENT");

        JsonNode second = trigger("BONUS", "dup-key-1");
        assertThat(second.get("duplicate").asBoolean()).isTrue();
        assertThat(second.get("eventId").asText()).isEqualTo(eventId);
        mvc.perform(post("/api/demo/liquidity-events/" + eventId + "/replay")).andExpect(status().isAccepted());

        await().atMost(Duration.ofSeconds(5)).until(() ->
                auditActions(eventId).stream().filter(a -> a.equals("DUPLICATE_EVENT_DETECTED")).count() >= 2);
        assertThat(balance("CURRENT")).isEqualTo(balanceAfterFirst);
        assertThat(auditActions(eventId).stream().filter(a -> a.equals("RECOMMENDATION_GENERATED")).count()).isEqualTo(1);
        assertThat(json("GET", "/api/recommendations/" + reco.get("id").asText(), null).get("actions"))
                .allMatch(a -> a.get("status").asText().equals("PROPOSED"));
    }

    @Test
    void declinedRecommendationExecutesNothing() throws Exception {
        String eventId = trigger("TAX_REFUND", null).get("eventId").asText();
        JsonNode reco = awaitRecommendation(eventId);
        String recoId = reco.get("id").asText();
        JsonNode declined = json("POST", "/api/recommendations/" + recoId + "/decline", "{}");
        assertThat(declined.get("status").asText()).isEqualTo("DECLINED");
        assertThat(declined.get("actions")).allMatch(a -> a.get("status").asText().equals("CANCELLED"));
        String action = reco.get("actions").get(0).get("id").asText();
        assertThat(statusOf("/api/actions/" + action + "/approve", "{\"confirmed\":true}")).isEqualTo(409);
        assertThat(statusOf("/api/actions/" + action + "/execute", null)).isEqualTo(403);
        assertThat(balance("CREDIT_CARD")).isEqualTo(32000.0);
        assertThat(auditActions(eventId)).contains("CUSTOMER_DECLINED").doesNotContain("ACTION_EXECUTED");
    }

    @Test
    void creditBureauOutageDegradesToCachedContext() throws Exception {
        json("POST", "/api/demo/toggles", "{\"creditBureauDown\":true}");
        JsonNode reco = awaitRecommendation(trigger("TWO_POT_PAYOUT", null).get("eventId").asText());
        assertThat(reco.get("reducedContext").asBoolean()).isTrue();
        assertThat(reco.get("contextNotes").toString()).contains("Using cached customer context");
        assertThat(reco.get("actions").get(0).get("type").asText()).isEqualTo("DEBT_REPAYMENT");
        assertThat(reco.get("narrative").get("confidence").asDouble()).isLessThan(0.87);
    }

    @Test
    void aiOutageFallsBackToDeterministicRules() throws Exception {
        json("POST", "/api/demo/toggles", "{\"aiDown\":true}");
        JsonNode reco = awaitRecommendation(trigger("TWO_POT_PAYOUT", null).get("eventId").asText());
        assertThat(reco.get("source").asText()).isEqualTo("RULES_FALLBACK");
        assertThat(reco.get("actions")).hasSize(3);
        assertThat(reco.get("narrative").get("confidence").isNull()).isTrue();
    }

    @Test
    void inputValidationRejectsBadRequests() throws Exception {
        assertThat(statusOf("/api/demo/liquidity-events", "{\"scenario\":\"NOPE\"}")).isEqualTo(400);
        assertThat(statusOf("/api/demo/liquidity-events", "{\"scenario\":\"BONUS\",\"amount\":-5}")).isEqualTo(400);
        assertThat(statusOf("/api/demo/liquidity-events", "{\"scenario\":\"BONUS\",\"customerId\":\"x; drop\"}")).isEqualTo(400);
        assertThat(statusOf("/api/demo/liquidity-events", "{\"scenario\":\"BONUS\",\"customerId\":\"CUST-9999\"}")).isEqualTo(404);
        mvc.perform(get("/api/recommendations/REC-NOPE")).andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}