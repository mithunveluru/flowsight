package com.flowsight.ml.categorization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlProperties;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.ml.PredictionLogEntry;
import com.flowsight.ml.PredictionLogService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The categorizer against a stub ml-service: every failure path keeps the rules answer.
class TransactionCategorizerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private volatile long delayMs = 0;

    private final PredictionLogService predictionLog = mock(PredictionLogService.class);
    private final PersonalizationService personalization = mock(PersonalizationService.class);
    private final UUID userId = UUID.randomUUID();
    private final UUID otherUser = UUID.randomUUID();
    private final ReviewedHistory history = ReviewedHistory.inMemory(Map.of(
        userId, List.of(new ReviewedHistory.Reviewed("Moonlight Grill", "POS MOONLIGHT GRILL DUBAI", TransactionCategory.GROCERIES)),
        otherUser, List.of(new ReviewedHistory.Reviewed("Starbucks", "STARBUCKS DUBAI", TransactionCategory.TRAVEL))));

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            JsonNode body = JSON.readTree(exchange.getRequestBody().readAllBytes());
            requests.add(body);
            try {
                if (delayMs > 0) Thread.sleep(delayMs);
                byte[] bytes = reply(body).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        when(personalization.isEnabled(userId)).thenReturn(true);
        when(personalization.isEnabled(otherUser)).thenReturn(true);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    // FOOD_DINING 0.8 for every item, alternatives GROCERIES 0.1 and SHOPPING 0.05
    private static String reply(JsonNode body) {
        List<String> predictions = new ArrayList<>();
        for (int i = 0; i < body.path("items").size(); i++) {
            predictions.add("""
                {"category":"FOOD_DINING","probability":0.8,"alternatives":[
                  {"category":"FOOD_DINING","probability":0.8},{"category":"GROCERIES","probability":0.1},
                  {"category":"SHOPPING","probability":0.05}]}""");
        }
        return "{\"request_id\":\"r\",\"capability\":\"categorizer\",\"model_version\":\"tfidf-lr-1\",\"confidence\":0.8,"
            + "\"summary\":{},\"result\":{\"predictions\":[" + String.join(",", predictions) + "]}}";
    }

    private TransactionCategorizer categorizer(boolean enabled, boolean shadow) {
        MlProperties props = new MlProperties("http://127.0.0.1:" + server.getAddress().getPort(), "token",
            Duration.ofMillis(250), 5, Duration.ofSeconds(30),
            Map.of("categorizer", new MlProperties.CapabilityFlags(enabled, shadow, Duration.ofMillis(300))));
        return new TransactionCategorizer(new MlClient(props, JSON, predictionLog), personalization, history);
    }

    private static Transaction rulesRow(String merchant, String description) {
        return Transaction.builder()
            .merchant(merchant)
            .description(description)
            .amount(BigDecimal.TEN)
            .type(TransactionType.DEBIT)
            .category(TransactionCategory.UNCATEGORIZED)
            .confidenceScore(new BigDecimal("0.2500"))
            .categorySource(CategorySource.RULES)
            .build();
    }

    private static void assertRules(Transaction tx) {
        assertThat(tx.getCategory()).isEqualTo(TransactionCategory.UNCATEGORIZED);
        assertThat(tx.getCategorySource()).isEqualTo(CategorySource.RULES);
        assertThat(tx.getCategoryModelVersion()).isNull();
        assertThat(tx.getCategoryAlternatives()).isNull();
    }

    @Test
    void flagOffKeepsRulesWithoutCalling() {
        Transaction tx = rulesRow("Fresh Mart", "POS FRESH MART");

        categorizer(false, false).refine(userId, List.of(tx));

        assertRules(tx);
        assertThat(requests).isEmpty();
    }

    @Test
    void enabledModelReplacesRulesWithItsAnswer() {
        Transaction tx = rulesRow("Fresh Mart", "POS FRESH MART");

        categorizer(true, false).refine(userId, List.of(tx));

        assertThat(tx.getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
        assertThat(tx.getCategorySource()).isEqualTo(CategorySource.MODEL);
        assertThat(tx.getCategoryModelVersion()).isEqualTo("tfidf-lr-1");
        assertThat(tx.getConfidenceScore()).isEqualByComparingTo("0.8000");
        assertThat(tx.getCategoryAlternatives()).extracting(CategoryAlternative::category)
            .containsExactly(TransactionCategory.FOOD_DINING, TransactionCategory.GROCERIES, TransactionCategory.SHOPPING);
        JsonNode item = requests.get(0).path("items").get(0);
        assertThat(item.path("merchant").asText()).isEqualTo("Fresh Mart");
        assertThat(item.path("direction").asText()).isEqualTo("DEBIT");
    }

    @Test
    void serverErrorKeepsRules() {
        status.set(500);
        Transaction tx = rulesRow("Fresh Mart", "POS FRESH MART");

        categorizer(true, false).refine(userId, List.of(tx));

        assertRules(tx);
        assertThat(requests).hasSize(1);
    }

    @Test
    void slowServiceKeepsRules() {
        delayMs = 1500;
        Transaction tx = rulesRow("Fresh Mart", "POS FRESH MART");
        long started = System.nanoTime();

        categorizer(true, false).refine(userId, List.of(tx));

        assertRules(tx);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1200));
    }

    @Test
    void malformedAnswerKeepsRules() {
        Transaction tx = rulesRow("Fresh Mart", "POS FRESH MART");
        status.set(200);
        server.removeContext("/");
        server.createContext("/", exchange -> {
            byte[] bytes = "{\"model_version\":\"x\",\"result\":{\"predictions\":[{\"category\":\"NOT_A_CATEGORY\",\"probability\":0.9,\"alternatives\":[]}]}}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        categorizer(true, false).refine(userId, List.of(tx));

        assertRules(tx);
    }

    @Test
    void shadowServesRulesAndLogsTheModel() {
        Transaction tx = rulesRow("Fresh Mart", "POS FRESH MART");

        categorizer(false, true).refine(userId, List.of(tx));

        assertRules(tx);
        ArgumentCaptor<PredictionLogEntry> logged = ArgumentCaptor.forClass(PredictionLogEntry.class);
        verify(predictionLog, timeout(2000)).record(logged.capture());
        assertThat(logged.getValue().getMode()).isEqualTo(PredictionLogEntry.Mode.SHADOW);
        assertThat(logged.getValue().getModelVersion()).isEqualTo("tfidf-lr-1");
        assertThat(logged.getValue().getUserId()).isEqualTo(userId);
        assertThat(requests).hasSize(1);
    }

    @Test
    void userAndMerchantRuleCategoriesAreNeverSent() {
        Transaction user = rulesRow("A", "A");
        user.setCategorySource(CategorySource.USER);
        Transaction rule = rulesRow("B", "B");
        rule.setCategorySource(CategorySource.PERSONAL);

        categorizer(true, false).refine(userId, List.of(user, rule));

        assertThat(requests).isEmpty();
        assertThat(user.getCategorySource()).isEqualTo(CategorySource.USER);
    }

    @Test
    void reviewedHistoryOfTheSameUserPersonalizes() {
        Transaction tx = rulesRow("Moonlight Grill", "POS MOONLIGHT GRILL DUBAI");

        categorizer(true, false).refine(userId, List.of(tx));

        assertThat(tx.getCategory()).isEqualTo(TransactionCategory.GROCERIES);
        assertThat(tx.getCategorySource()).isEqualTo(CategorySource.PERSONAL);
        assertThat(tx.getConfidenceScore()).isEqualByComparingTo("0.5500");
    }

    @Test
    void anotherUsersHistoryIsNotUsed() {
        Transaction tx = rulesRow("Starbucks", "STARBUCKS DUBAI");

        categorizer(true, false).refine(userId, List.of(tx));

        assertThat(tx.getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
        assertThat(tx.getCategorySource()).isEqualTo(CategorySource.MODEL);
    }

    @Test
    void personalizationOffUsesTheGlobalModelOnly() {
        when(personalization.isEnabled(userId)).thenReturn(false);
        Transaction tx = rulesRow("Moonlight Grill", "POS MOONLIGHT GRILL DUBAI");

        categorizer(true, false).refine(userId, List.of(tx));

        assertThat(tx.getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
        assertThat(tx.getCategorySource()).isEqualTo(CategorySource.MODEL);
    }

    @Test
    void largeImportsGoInBatchesOfAHundred() {
        List<Transaction> rows = new ArrayList<>();
        for (int i = 0; i < 150; i++) rows.add(rulesRow("Shop " + i, "POS SHOP " + i));

        categorizer(true, false).refine(userId, rows);

        assertThat(requests).extracting(r -> r.path("items").size()).containsExactly(100, 50);
        assertThat(rows).allMatch(t -> t.getCategorySource() == CategorySource.MODEL);
    }
}
