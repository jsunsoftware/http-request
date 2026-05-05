package com.jsunsoft.http;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

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
    void disallowPrivateAndLoopbackHosts_blocksLoopbackAndPrivateAddressesAtConnectTime() {
        // The opt-in SSRF guard is plumbed through Apache HC5's DnsResolver: when enabled, any
        // host that resolves to loopback / private / link-local fails the lookup with
        // UnknownHostException, which the library wraps into a ResponseException at the request
        // boundary. The check fires not only on the original URL but on every redirect target.
        try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient guardedClient =
                     ClientBuilder.create()
                             .disallowPrivateAndLoopbackHosts()
                             .setConnectionRequestTimeout(500)
                             .setResponseTimeout(500)
                             .setConnectTimeout(500)
                             .build()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(guardedClient).build();

            // For each blocked address, the request must fail (the resolver throws
            // UnknownHostException, which the library surfaces as a non-success ResponseHandler).
            assertFalse(httpRequest.target("http://127.0.0.1/").get(String.class).isSuccess(),
                    "127.0.0.1 must not resolve under SSRF guard");
            assertFalse(httpRequest.target("http://localhost/").get(String.class).isSuccess(),
                    "localhost must not resolve under SSRF guard");
            assertFalse(httpRequest.target("http://10.0.0.1/").get(String.class).isSuccess(),
                    "10.0.0.1 (RFC 1918) must not resolve under SSRF guard");
            assertFalse(httpRequest.target("http://192.168.1.1/").get(String.class).isSuccess(),
                    "192.168.1.1 (RFC 1918) must not resolve under SSRF guard");
            assertFalse(httpRequest.target("http://172.16.0.1/").get(String.class).isSuccess(),
                    "172.16.0.1 (RFC 1918) must not resolve under SSRF guard");
            assertFalse(httpRequest.target("http://169.254.169.254/").get(String.class).isSuccess(),
                    "169.254.169.254 (cloud-metadata) must not resolve under SSRF guard");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void disallowPrivateAndLoopbackHosts_offByDefault() {
        // The guard is opt-in — without calling disallowPrivateAndLoopbackHosts() on the
        // ClientBuilder, the default DNS resolver applies and loopback resolves normally.
        // (We don't actually issue a request — just verify the URI parses cleanly at target() time.)
        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build()).build();
        httpRequest.target("http://127.0.0.1/foo"); // must not throw
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


