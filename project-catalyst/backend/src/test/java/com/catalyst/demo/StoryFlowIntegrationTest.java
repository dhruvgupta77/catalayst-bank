package com.catalyst.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 * The refined story: context + signals, pipeline trace, accept / modify / decline, policy and compliance blocks.
 */
@SpringBootTest(properties = "catalyst.execution-step-delay-ms=0")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class StoryFlowIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(post("/api/demo/reset"));
    }

    private JsonNode getJson(String url) throws Exception {
        return mapper.readTree(mvc.perform(get(url)).andReturn().getResponse().getContentAsString());
    }

    private int send(String url, String body, JsonNode[] out) throws Exception {
        var res = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        if (out != null) out[0] = mapper.readTree(res.getContentAsString());
        return res.getStatus();
    }

    private JsonNode twoPot() throws Exception {
        JsonNode[] r = new JsonNode[1];
        send("/api/demo/liquidity-events", "{\"scenario\":\"TWO_POT_WITHDRAWAL\"}", r);
        String eventId = r[0].get("eventId").asText();
        await().atMost(Duration.ofSeconds(10)).until(() -> getJson("/api/liquidity-events/" + eventId).hasNonNull("recommendationId"));
        return getJson("/api/recommendations/" + getJson("/api/liquidity-events/" + eventId).get("recommendationId").asText());
    }

    private List<String> audit(String eventId) throws Exception {
        return StreamSupport.stream(getJson("/api/audit/" + eventId).get("entries").spliterator(), false)
                .map(n -> n.get("action").asText()).toList();
    }

    private double balance(String type) throws Exception {
        for (JsonNode a : getJson("/api/customers/CUST-1001").get("accounts"))
            if (a.get("type").asText().equals(type)) return a.get("balance").asDouble();
        throw new IllegalStateException(type);
    }

    @Test
    void financialContextExposesLabelledSignalsBeforeAnyEvent() throws Exception {
        JsonNode ctx = getJson("/api/customers/CUST-1001/financial-context");
        assertThat(ctx.get("label").asText()).contains("Illustrative");
        assertThat(ctx.get("disclaimer").asText()).contains("not a predictive model");
        List<String> ids = StreamSupport.stream(ctx.get("signals").spliterator(), false).map(s -> s.get("id").asText()).toList();
        assertThat(ids).contains("HIGH_REVOLVING_DEBT", "LIMITED_EMERGENCY_BUFFER", "LOW_AVAILABLE_BALANCE");
        assertThat(ctx.get("creditCardBalance").asInt()).isEqualTo(32000);
    }

    @Test
    void twoPotAliasAndLargeTransferAreSupportedAndPipelineIsTraceable() throws Exception {
        JsonNode reco = twoPot();
        assertThat(reco.get("event").get("type").asText()).isEqualTo("TWO_POT_PAYOUT");
        assertThat(reco.get("signals").size()).isGreaterThanOrEqualTo(3);

        JsonNode p = getJson("/api/pipeline/" + reco.get("eventId").asText());
        assertThat(p.get("principle").asText()).contains("The customer decides");
        assertThat(p.get("stages")).hasSize(7);
        assertThat(p.get("stages").get(3).get("state").asText()).isEqualTo("DONE");
        assertThat(p.get("stages").get(4).get("state").asText()).isEqualTo("WAITING");
        assertThat(p.get("agents")).hasSize(4);
        assertThat(p.get("agents").get(3).get("kind").asText()).isEqualTo("AI");

        JsonNode[] r = new JsonNode[1];
        assertThat(send("/api/demo/liquidity-events", "{\"scenario\":\"LARGE_TRANSFER\"}", r)).isEqualTo(202);
    }

    @Test
    void modifyReducesAmountAndIsAuditedThenExecutesOnlyTheModifiedAmount() throws Exception {
        JsonNode reco = twoPot();
        String recoId = reco.get("id").asText();
        String debt = reco.get("actions").get(0).get("id").asText();
        double cardBefore = balance("CREDIT_CARD");
        int status = send("/api/recommendations/" + recoId + "/consent",
                "{\"confirmed\":true,\"actionIds\":[\"" + debt + "\"],\"amounts\":{\"" + debt + "\":12000}}", null);
        assertThat(status).isEqualTo(200);
        await().atMost(Duration.ofSeconds(10)).until(() -> getJson("/api/actions/" + debt).get("status").asText().equals("EXECUTED"));
        assertThat(balance("CREDIT_CARD")).isEqualTo(cardBefore - 12000);
        assertThat(audit(reco.get("eventId").asText())).containsSubsequence("CUSTOMER_MODIFIED",
                "CUSTOMER_APPROVED", "ACTION_EXECUTED");
        assertThat(getJson("/api/pipeline/" + reco.get("eventId").asText()).get("stages").get(5).get("state").asText()).isEqualTo("DONE");
    }

    @Test
    void increasingAnAmountBeyondRuleLimitIsBlockedByPolicy() throws Exception {
        JsonNode reco = twoPot();
        String recoId = reco.get("id").asText();
        String debt = reco.get("actions").get(0).get("id").asText();
        double cardBefore = balance("CREDIT_CARD");
        assertThat(send("/api/recommendations/" + recoId + "/consent",
                "{\"confirmed\":true,\"actionIds\":[\"" + debt + "\"],\"amounts\":{\"" + debt + "\":25000}}", null)).isEqualTo(422);
        assertThat(balance("CREDIT_CARD")).isEqualTo(cardBefore);
        assertThat(getJson("/api/recommendations/" + recoId).get("status").asText()).isNotEqualTo("APPROVED");
        assertThat(audit(reco.get("eventId").asText())).contains("POLICY_BLOCKED").doesNotContain("ACTION_EXECUTED");
        assertThat(getJson("/api/pipeline/" + reco.get("eventId").asText()).get("stages").get(4).get("state").asText()).isEqualTo("BLOCKED");
    }

    @Test
    void declineAuditsNoActionExecuted() throws Exception {
        JsonNode reco = twoPot();
        assertThat(send("/api/recommendations/" + reco.get("id").asText() + "/decline", "{}", null)).isEqualTo(200);
        assertThat(audit(reco.get("eventId").asText())).containsSubsequence("CUSTOMER_DECLINED", "NO_ACTION_EXECUTED");
        assertThat(getJson("/api/pipeline/" + reco.get("eventId").asText()).get("stages").get(5).get("state").asText()).isEqualTo("SKIPPED");
        assertThat(balance("CREDIT_CARD")).isEqualTo(32000.0);
    }

    @Test
    void complianceBlockStopsExecutionEvenWithConsent() throws Exception {
        assertThat(send("/api/demo/toggles", "{\"complianceBlock\":true}", null)).isEqualTo(200);
        JsonNode reco = twoPot();
        String debt = reco.get("actions").get(0).get("id").asText();
        send("/api/recommendations/" + reco.get("id").asText() + "/consent",
                "{\"confirmed\":true,\"actionIds\":[\"" + debt + "\"]}", null);
        await().atMost(Duration.ofSeconds(10)).until(() -> getJson("/api/actions/" + debt).get("status").asText().equals("FAILED"));
        assertThat(getJson("/api/actions/" + debt).get("failureReason").asText()).startsWith("Blocked by compliance");
        assertThat(balance("CREDIT_CARD")).isEqualTo(32000.0);
        assertThat(audit(reco.get("eventId").asText())).contains("ACTION_BLOCKED").doesNotContain("ACTION_EXECUTED");
        assertThat(getJson("/api/pipeline/" + reco.get("eventId").asText()).get("stages").get(5).get("state").asText()).isEqualTo("BLOCKED");
    }

    @Test
    void missingConsentBlocksExecutionAndIsAudited() throws Exception {
        JsonNode reco = twoPot();
        String debt = reco.get("actions").get(0).get("id").asText();
        assertThat(send("/api/actions/" + debt + "/execute", "{}", null)).isEqualTo(403);
        assertThat(audit(reco.get("eventId").asText())).contains("ACTION_REJECTED");
        assertThat(balance("CREDIT_CARD")).isEqualTo(32000.0);
    }
}