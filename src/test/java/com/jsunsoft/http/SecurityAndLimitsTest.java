package com.jsunsoft.http;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityAndLimitsTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @Test
    void uriSchemeValidation_rejectsDisallowedSchemes_whenConfigured() {
        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
                .allowHttpAndHttpsOnly()
                .build();

        assertThrows(IllegalArgumentException.class, () -> httpRequest.target("file:///etc/passwd"));
    }

    @Test
    void uriSchemeValidation_isCaseInsensitive() {
        // Pins the BasicHttpRequest#validateUriScheme contract: the URI's scheme is normalised to
        // Locale.ROOT lowercase before lookup against the allow-list, so uppercase / mixed-case
        // schemes match an allow-list entry stored in lowercase form. A future "simplification"
        // that drops the toLowerCase call would silently reject HTTPS://example.com — this test
        // is the regression guard.
        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
                .allowHttpAndHttpsOnly()
                .build();

        // None of these should throw; we don't actually issue requests, just construct the targets.
        httpRequest.target("HTTPS://example.com/foo");
        httpRequest.target("HtTpS://example.com/foo");
        httpRequest.target("HTTP://example.com/foo");
    }

    @Test
    void uriSchemeValidation_rejectsRelativeOrSchemeLessUris_whenAllowListIsActive() {
        // Relative URIs (no scheme) must not slip past the allow-list — URI.getScheme() returns
        // null and the validator's null-check is the only thing standing between this and a
        // misrouted request. Pinned because skipping the null-check is an easy oversight when
        // refactoring.
        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
                .allowHttpAndHttpsOnly()
                .build();

        assertThrows(IllegalArgumentException.class, () -> httpRequest.target("/just/a/path"));
        assertThrows(IllegalArgumentException.class, () -> httpRequest.target("//example.com/foo"));
    }

    @Test
    void responseSizeLimit_enforced_forJsonDeserialization() {
        String largeJson = "{\"data\":\"" + "a".repeat(5000) + "\"}";
        wm.stubFor(get(urlEqualTo("/large"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", APPLICATION_JSON.toString())
                        .withBody(largeJson)));

        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
                .setMaxResponseBodySizeBytes(512)
                .build();

        // Deserialization should fail due to size limit; library maps this to a non-success handler.
        ResponseHandler<ResponseData> rh = httpRequest
                .target(wm.getRuntimeInfo().getHttpBaseUrl())
                .path("large")
                .get(ResponseData.class);

        // Must remain SC_BAD_GATEWAY (502) and not be masked by close() failures.
        assertEquals(502, rh.getCode());
        assertEquals(200, rh.getOriginalCode());
    }

    @Test
    void basicAuthChecked_rejectsColonInUsername_and_charArrayIsCleared() {
        HttpRequestBuilder builder = HttpRequestBuilder.create(new ClientBuilder().build());

        assertThrows(IllegalArgumentException.class, () -> builder.basicAuth("user:name", "pw"));

        char[] password = "secret".toCharArray();
        builder.basicAuth("admin", password);

        // Password should be cleared after use.
        for (char c : password) {
            assertEquals('\0', c);
        }
    }

    static class ResponseData {
        public String data;
    }
}


