package com.jsunsoft.http;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestBodyConverterTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @Test
    void customRequestBodyConverter_isUsed_forObjectPayload() {
        ContentType ct = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);
        wm.stubFor(post(urlEqualTo("/plain"))
                .withHeader("Content-Type", equalTo(ct.toString()))
                .withRequestBody(equalTo("HELLO:world"))
                .willReturn(aResponse().withStatus(200)));

        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
                .addRequestBodyConverter(new RequestBodyConverter() {
                    @Override
                    public boolean canConvert(RequestBodyConverterContext context) {
                        return ct.isSameMimeType(context.getContentType()) && context.getBody() instanceof Payload;
                    }

                    @Override
                    public HttpEntity convert(RequestBodyConverterContext context) {
                        Payload p = (Payload) context.getBody();
                        return new StringEntity("HELLO:" + p.value, StandardCharsets.UTF_8);
                    }
                })
                .build();

        ResponseHandler<?> rh = httpRequest
                .target(wm.getRuntimeInfo().getHttpBaseUrl())
                .path("plain")
                .addContentType(ct)
                .rawPost(new Payload("world"));

        assertEquals(200, rh.getCode());
    }

    @Test
    void defaultRequestBodyConverter_stillSerializesJson() {
        ContentType ct = ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8);
        wm.stubFor(post(urlEqualTo("/json"))
                .withHeader("Content-Type", equalTo(ct.toString()))
                .withRequestBody(matchingJsonPath("$.value", equalTo("world")))
                .willReturn(aResponse().withStatus(200)));

        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build()).build();

        ResponseHandler<?> rh = httpRequest
                .target(wm.getRuntimeInfo().getHttpBaseUrl())
                .path("json")
                .addContentType(ct)
                .rawPost(new Payload("world"));

        assertEquals(200, rh.getCode());
    }

    static class Payload {
        public final String value;

        Payload(String value) {
            this.value = value;
        }
    }
}


