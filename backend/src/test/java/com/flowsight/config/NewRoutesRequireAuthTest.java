package com.flowsight.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Routes added since the first release; the real filter chain must answer 401 without a token.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NewRoutesRequireAuthTest {

    @Autowired private MockMvc mvc;

    private record Route(HttpMethod method, String path) {}

    @Test
    void everyNewRouteRejectsAnonymousCalls() throws Exception {
        String id = UUID.randomUUID().toString();
        List<Route> routes = List.of(
            new Route(HttpMethod.PATCH, "/api/v1/account/preferences"),
            new Route(HttpMethod.POST, "/api/v1/assistant/ask"),
            new Route(HttpMethod.GET, "/api/v1/forecast"),
            new Route(HttpMethod.GET, "/api/v1/merchant-rules"),
            new Route(HttpMethod.DELETE, "/api/v1/merchant-rules/" + id),
            new Route(HttpMethod.POST, "/api/v1/merchant-rules/" + id + "/preview"),
            new Route(HttpMethod.POST, "/api/v1/merchant-rules/" + id + "/apply"),
            new Route(HttpMethod.GET, "/api/v1/onboarding/sample-data"),
            new Route(HttpMethod.POST, "/api/v1/onboarding/sample-data"),
            new Route(HttpMethod.DELETE, "/api/v1/onboarding/sample-data"),
            new Route(HttpMethod.POST, "/api/v1/insights/recommendations/" + id + "/accept"),
            new Route(HttpMethod.POST, "/api/v1/insights/recommendations/" + id + "/dismiss"),
            new Route(HttpMethod.GET, "/api/v1/transactions/review-queue"),
            new Route(HttpMethod.PATCH, "/api/v1/transactions/" + id),
            new Route(HttpMethod.GET, "/api/v1/receipts/" + id + "/image"),
            new Route(HttpMethod.GET, "/api/v1/receipts"),
            new Route(HttpMethod.POST, "/api/v1/receipts/" + id + "/confirm"),
            new Route(HttpMethod.POST, "/api/v1/simulate"),
            new Route(HttpMethod.GET, "/api/v1/leaks"),
            new Route(HttpMethod.GET, "/api/v1/recurring"));
        for (Route r : routes) {
            mvc.perform(request(r.method(), r.path()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        }
    }
}
