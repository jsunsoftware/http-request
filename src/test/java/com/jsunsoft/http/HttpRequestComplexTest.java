package com.jsunsoft.http;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpRequestComplexTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @Test
    void testBasicAuthCorrectnessWithCharArray() {
        String username = "testUser";
        char[] password = "complex&Password123".toCharArray();
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString((username + ":" + new String(password)).getBytes(StandardCharsets.UTF_8));

        wm.stubFor(get(urlEqualTo("/secure"))
                .withHeader("Authorization", equalTo(expectedAuth))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Access Granted")));

        // Create HttpRequest with Basic Auth using char[]
        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
                .basicAuth(username, password)
                .build();

        // Verify password array is cleared
        for (char c : password) {
            assertEquals('\0', c, "Password char array should be cleared after use");
        }

        ResponseHandler<String> response = httpRequest.target(wm.getRuntimeInfo().getHttpBaseUrl())
                .path("/secure")
                .get(String.class);

        assertEquals(HttpStatus.SC_OK, response.getCode());
        assertEquals("Access Granted", response.orElse(null));
    }

    @Test
    void testComplexCrudWorkflow() {
        // 1. Setup Client
        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
                .addContentType(ContentType.APPLICATION_JSON)
                .enableRequestPayloadLogging() // Enable logging to test that it doesn't crash
                .build();

        String baseUrl = wm.getRuntimeInfo().getHttpBaseUrl();
        WebTarget target = httpRequest.target(baseUrl).path("/users");

        // 2. Create User (POST)
        String newUserJson = "{\"name\":\"John Doe\", \"email\":\"john@example.com\"}";
        wm.stubFor(post(urlEqualTo("/users"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson(newUserJson))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Location", baseUrl + "/users/123")
                        .withBody("{\"id\":\"123\"}")));

        ResponseHandler<UserResponse> createResponse = target.post(new UserRequest("John Doe", "john@example.com"), UserResponse.class);

        assertEquals(HttpStatus.SC_CREATED, createResponse.getCode());
        assertNotNull(createResponse.get());
        assertEquals("123", createResponse.get().id);

        // 3. Get User (GET)
        wm.stubFor(get(urlEqualTo("/users/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"123\", \"name\":\"John Doe\", \"email\":\"john@example.com\"}")));

        ResponseHandler<User> getResponse = httpRequest.target(baseUrl).path("/users/123").get(User.class);

        assertEquals(HttpStatus.SC_OK, getResponse.getCode());
        assertEquals("John Doe", getResponse.get().name);

        // 4. Update User (PUT)
        String updateUserJson = "{\"name\":\"Jane Doe\", \"email\":\"jane@example.com\"}";
        wm.stubFor(put(urlEqualTo("/users/123"))
                .withRequestBody(equalToJson(updateUserJson))
                .willReturn(aResponse().withStatus(200))); // Or 204

        ResponseHandler<String> updateResponse = httpRequest.target(baseUrl).path("/users/123")
                .put(new UserRequest("Jane Doe", "jane@example.com"), String.class);

        assertEquals(HttpStatus.SC_OK, updateResponse.getCode());

        // 5. Delete User (DELETE)
        wm.stubFor(delete(urlEqualTo("/users/123"))
                .willReturn(aResponse().withStatus(204)));

        ResponseHandler<Void> deleteResponse = httpRequest.target(baseUrl).path("/users/123").delete(Void.class);

        assertTrue(HttpRequestUtils.isSuccess(deleteResponse.getCode()));
        assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.getCode());

        // 6. Verify User Deleted (GET -> 404)
        wm.stubFor(get(urlEqualTo("/users/123"))
                .willReturn(aResponse().withStatus(404)));

        ResponseHandler<String> notFoundResponse = httpRequest.target(baseUrl).path("/users/123").get(String.class);

        assertEquals(HttpStatus.SC_NOT_FOUND, notFoundResponse.getCode());
    }

    @Test
    void testRetryLogic_withFlakyServer() {
        // Fail twice, then succeed
        wm.stubFor(get(urlEqualTo("/flaky"))
                .inScenario("Flaky Scenario")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Attempt 1 Failed"));

        wm.stubFor(get(urlEqualTo("/flaky"))
                .inScenario("Flaky Scenario")
                .whenScenarioStateIs("Attempt 1 Failed")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("Attempt 2 Failed"));

        wm.stubFor(get(urlEqualTo("/flaky"))
                .inScenario("Flaky Scenario")
                .whenScenarioStateIs("Attempt 2 Failed")
                .willReturn(aResponse().withStatus(200).withBody("Success")));

        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build()).build();

        // Configure retry context: 3 retries, retry on 503 (GET is idempotent — default gate is
        // not an obstacle here, but we override mustBeRetried explicitly for clarity).
        RetryContext retryContext = new RetryContext() {
            @Override
            public int getRetryCount() {
                return 3;
            }

            @Override
            public boolean mustBeRetried(RetryAttempt attempt) {
                return attempt.getResponse() != null && attempt.getResponse().getCode() == 503;
            }

            @Override
            public Duration getRetryDelay(RetryAttempt attempt) {
                return Duration.ofSeconds(1);
            }
        };

        ResponseHandler<String> response = httpRequest.retryableTarget(wm.getRuntimeInfo().getHttpBaseUrl(), retryContext)
                .path("/flaky")
                .get(String.class);

        assertEquals(HttpStatus.SC_OK, response.getCode());
        assertEquals("Success", response.get());
    }

    static class UserRequest {
        public String name;
        public String email;

        public UserRequest(String name, String email) {
            this.name = name;
            this.email = email;
        }
    }

    static class UserResponse {
        public String id;
    }

    static class User {
        public String id;
        public String name;
        public String email;
    }
}

