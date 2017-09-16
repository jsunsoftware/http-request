/*
 * Copyright 2017 Benik Arakelyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jsunsoft.http;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.http.HttpHeaders;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.entity.ContentType.APPLICATION_XML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpRequestSimpleTest {
    @Rule
    public final WireMockRule wireMockRule = new WireMockRule(8080);
    private final String userAgent = "JsunSoftAgent/1.0";

    private final HttpRequest<?> httpRequestUserAgent = HttpRequestBuilder.createGet("http://localhost:8080/userAgent")
            .addDefaultHeader(HttpHeaders.USER_AGENT, userAgent).build();

    private final HttpRequest<XmlWrapper> xmlHttpRequest = HttpRequestBuilder.createPost("http://localhost:8080/xml", XmlWrapper.class)
            .contentTypeOfBody(APPLICATION_XML)
            .build();


    @Test
    public void userAgentTest() {
        wireMockRule.stubFor(get(urlEqualTo("/userAgent"))
                .withHeader("User-Agent", equalTo(userAgent))
                .willReturn(aResponse().withStatus(200)));

        assertTrue(httpRequestUserAgent.execute().isSuccess());
    }

    @Test
    public void xmlTest() {
        String xmlBody = "<xml><id>1</id></xml>";
        wireMockRule.stubFor(post(urlEqualTo("/xml"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_XML.getMimeType()))
                .withRequestBody(equalTo(xmlBody))
                .willReturn(
                        aResponse()
                                .withBody(xmlBody)
                                .withHeader(CONTENT_TYPE, APPLICATION_XML.getMimeType())
                                .withStatus(200)
                )
        );

        ResponseHandler<XmlWrapper> responseHandler = xmlHttpRequest.executeWithBody(xmlBody);

        assertTrue(responseHandler.isSuccess());
        assertTrue(responseHandler.hasContent());

        XmlWrapper parsedXml = xmlHttpRequest.executeWithBody(xmlBody).get();

        assertEquals(1, parsedXml.id);
    }

    public void proxyTest() {

    }

    private static class XmlWrapper {
        private int id;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }


}
