package com.jsunsoft.http;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
}


