package com.jsunsoft.http;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link ResponseHandler#getErrorText()} is safe to call even if the server returns
 * a non-success response without a body.
 */
class GetErrorTextTest {

    @RegisterExtension
    static WireMockExtension wireMockRule = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private static final HttpRequest HTTP_REQUEST =
            HttpRequestBuilder.create(new ClientBuilder().build()).build();

    @Test
    void getErrorText_doesNotThrow_whenNonSuccessResponseHasNoBody() {
        wireMockRule.stubFor(post(urlEqualTo("/empty-400"))
                .willReturn(aResponse().withStatus(400)));

        ResponseHandler<String> rh = HTTP_REQUEST.target(wireMockRule.getRuntimeInfo().getHttpBaseUrl())
                .path("empty-400")
                .post(String.class);

        assertTrue(rh.isNonSuccess());

        Assertions.assertDoesNotThrow(rh::getErrorText);
    }

    @Test
    void bodylessNonSuccess_doesNotConsultReaders_andHasNoErrorText() throws IOException {
        // A 304 (redirect handling is off by default, so it surfaces to the caller) carries no
        // body by spec — the error-text read must be skipped entirely: no reader consultation,
        // no "Can't find body reader" noise, errorText null. The probe reader deliberately omits
        // the hasEntity() guard: if the library consults readers for the entity-less response,
        // the synthetic value would leak into getErrorText().
        wireMockRule.stubFor(get(urlEqualTo("/not-modified"))
                .willReturn(aResponse().withStatus(304)));

        try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient client = new ClientBuilder().build()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(client)
                    .addBodyReader(new ResponseBodyReader<String>() {
                        @Override
                        public boolean isReadable(ResponseBodyReadableContext context) {
                            return context.getType() == String.class;   // deliberately no hasEntity() guard
                        }

                        @Override
                        public String read(ResponseBodyReaderContext<String> context) {
                            return "SYNTHETIC";
                        }
                    })
                    .build();

            ResponseHandler<String> rh = httpRequest.target(wireMockRule.getRuntimeInfo().getHttpBaseUrl())
                    .path("not-modified")
                    .get(String.class);

            Assertions.assertEquals(304, rh.getCode());
            Assertions.assertNull(rh.getErrorText(),
                    "a body-less non-success response must not consult readers or synthesize error text");
        }
    }

    @Test
    void getErrorText_returnsErrorText_whenNoErrorCause() {
        BasicResponseHandler<String> rh = newHandler("Bad request", null);

        Assertions.assertEquals("Bad request", rh.getErrorText());
    }

    @Test
    void getErrorText_returnsNull_whenErrorTextAndCauseAreMissing() {
        BasicResponseHandler<String> rh = newHandler(null, null);

        Assertions.assertNull(rh.getErrorText());
    }

    @Test
    void getErrorText_appendsCauseChain_whenErrorCausePresent() {
        IOException root = new IOException("IO failed");
        IllegalStateException mid = new IllegalStateException("State invalid", root);
        RuntimeException top = new RuntimeException("Top level", mid);

        BasicResponseHandler<String> rh = newHandler("Read failed", top);
        String error = rh.getErrorText();

        assertTrue(error.startsWith("Read failed. Reason: "));
        assertTrue(error.contains(top.toString()));
        assertTrue(error.contains(mid.toString()));
        assertTrue(error.contains(root.toString()));
        assertTrue(error.indexOf(top.toString()) < error.indexOf(mid.toString()));
        assertTrue(error.indexOf(mid.toString()) < error.indexOf(root.toString()));
    }

    @Test
    void getErrorText_trimsErrorText_beforeAppendingDot() {
        RuntimeException cause = new RuntimeException("Boom");
        BasicResponseHandler<String> rh = newHandler("Read failed   ", cause);

        String error = rh.getErrorText();

        assertTrue(error.startsWith("Read failed. Reason: "));
    }

    @Test
    void getErrorText_handlesNullErrorText_withErrorCause() {
        RuntimeException cause = new RuntimeException("Boom");
        BasicResponseHandler<String> rh = newHandler(null, cause);

        String error = rh.getErrorText();

        Assertions.assertNotNull(error);
        Assertions.assertEquals(cause.toString(), error);
    }

    @Test
    void getErrorText_returnsCauseOnly_whenErrorTextIsBlank() {
        RuntimeException cause = new RuntimeException("Boom");
        BasicResponseHandler<String> rh = newHandler("   ", cause);

        String error = rh.getErrorText();

        Assertions.assertEquals(cause.toString(), error);
    }

    @Test
    void getErrorText_handlesCauseWithNullMessage() {
        RuntimeException cause = new RuntimeException();
        BasicResponseHandler<String> rh = newHandler("Read failed", cause);

        String error = rh.getErrorText();

        assertTrue(error.contains("java.lang.RuntimeException"));
    }

    @Test
    void getErrorText_doesNotDuplicateDot_whenErrorTextAlreadyEndsWithDot() {
        RuntimeException cause = new RuntimeException("Boom");
        BasicResponseHandler<String> rh = newHandler("Read failed.", cause);

        String error = rh.getErrorText();

        assertTrue(error.startsWith("Read failed. Reason: "));
        Assertions.assertFalse(error.startsWith("Read failed.. Reason: "));
    }

    private static BasicResponseHandler<String> newHandler(String errorText, Exception errorCause) {
        return new BasicResponseHandler<>(null, 500, 500, new HeaderGroup(), errorCause, errorText, String.class, null,
                URI.create("http://localhost"), System.nanoTime());
    }
}
