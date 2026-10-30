/*
 * Copyright (c) 2026. Benik Arakelyan
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

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins how a failed body read is classified, identically for JSON, XML and text: the source stream
 * breaking (connection closed, read timeout) is a 503 carrying the transport {@link IOException};
 * a payload that can't be decoded or parsed — including a body that doesn't match its
 * {@code Content-Encoding} — is a 502 carrying a {@link ResponseBodyReaderException}.
 * The mid-body failures need a server that sends the headers and then breaks off, which WireMock
 * can't do — a one-shot raw socket server plays that part.
 */
class ResponseBodyReadFailureTest {

    // Without gzip: the Content-Encoding cases must reach the client exactly as stubbed.
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort().gzipDisabled(true))
            .build();

    private final List<ServerSocket> brokenServers = new ArrayList<>();
    private CloseableHttpClient client;
    private HttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        client = ClientBuilder.create().setResponseTimeout(300).build();
        httpRequest = HttpRequestBuilder.create(client).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        for (ServerSocket server : brokenServers) {
            server.close();
        }
    }

    @Test
    void xmlBody_connectionClosedMidBody_isServiceUnavailable() throws IOException {
        String url = brokenServer("application/xml", "<DummyData><data>abc", 0);

        ResponseHandler<DummyData> rh = httpRequest.target(url).get(DummyData.class);

        assertTransportFailure(rh, IOException.class);
    }

    @Test
    void jsonBody_connectionClosedMidBody_isServiceUnavailable() throws IOException {
        String url = brokenServer("application/json", "{\"data\":\"abc", 0);

        ResponseHandler<DummyData> rh = httpRequest.target(url).get(DummyData.class);

        assertTransportFailure(rh, IOException.class);
    }

    @Test
    void xmlBody_readTimeoutMidBody_isServiceUnavailable() throws IOException {
        String url = brokenServer("application/xml", "<DummyData><data>abc", 3000);

        ResponseHandler<DummyData> rh = httpRequest.target(url).get(DummyData.class);

        assertTransportFailure(rh, SocketTimeoutException.class);
    }

    @Test
    void xmlBody_connectionClosedMidBody_lazyReadEntityReportsStreamFailure() throws IOException {
        String url = brokenServer("application/xml", "<DummyData><data>abc", 0);

        try (Response response = httpRequest.target(url).get()) {
            ResponseBodyProcessingException e = assertThrows(ResponseBodyProcessingException.class,
                    () -> response.readEntity(DummyData.class));

            assertInstanceOf(IOException.class, e.getCause());
            assertFalse(e.getCause() instanceof ResponseBodyReaderException,
                    "A broken connection is not a deserialization failure, got: " + e.getCause());
        }
    }

    @Test
    void jsonBody_malformedThenConnectionDropped_isBadGateway() throws IOException {
        // The parse fails on the first bytes; disposing of the unread rest then fails too, because
        // the server has gone away. That later failure must not turn a payload error into a
        // transport error.
        String url = brokenServer("application/json", "{bad", 0);

        ResponseHandler<DummyData> rh = httpRequest.target(url).get(DummyData.class);

        assertPayloadFailure(rh);
    }

    @Test
    void jsonBody_undecodableUtf32_isBadGateway() {
        // Jackson detects UTF-32 from the zero bytes and decodes it with its own reader, which
        // reports the out-of-range code point as an IOException (CharConversionException).
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        "{\"data\":\"".chars().forEach(c -> body.writeBytes(utf32(c)));
        body.writeBytes(utf32(0x110000));
        "\"}".chars().forEach(c -> body.writeBytes(utf32(c)));
        wm.stubFor(get(urlEqualTo("/utf32")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body.toByteArray())));

        ResponseHandler<DummyData> rh = httpRequest.target(uri("/utf32")).get(DummyData.class);

        assertPayloadFailure(rh);
    }

    @Test
    void xmlBody_invalidUtf8_isBadGateway() {
        byte[] head = "<DummyData><data>".getBytes(StandardCharsets.UTF_8);
        byte[] tail = "</data></DummyData>".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(head);
        body.writeBytes(new byte[]{(byte) 0xC3, (byte) 0x28});
        body.writeBytes(tail);
        wm.stubFor(get(urlEqualTo("/bad-utf8")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/xml")
                .withBody(body.toByteArray())));

        ResponseHandler<DummyData> rh = httpRequest.target(uri("/bad-utf8")).get(DummyData.class);

        assertPayloadFailure(rh);
    }

    @Test
    void xmlBody_malformed_isBadGateway() {
        wm.stubFor(get(urlEqualTo("/bad-xml")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/xml")
                .withBody("<DummyData><data>abc</dat")));

        ResponseHandler<DummyData> rh = httpRequest.target(uri("/bad-xml")).get(DummyData.class);

        assertPayloadFailure(rh);
    }

    @Test
    void jsonBody_notMatchingItsContentEncoding_isBadGateway() {
        assertContentEncodingMismatchIsBadGateway("application/json", "{\"data\":\"abc\"}", DummyData.class);
    }

    @Test
    void xmlBody_notMatchingItsContentEncoding_isBadGateway() {
        assertContentEncodingMismatchIsBadGateway("application/xml", "<DummyData><data>abc</data></DummyData>", DummyData.class);
    }

    @Test
    void textBody_notMatchingItsContentEncoding_isBadGateway() {
        assertContentEncodingMismatchIsBadGateway("text/plain", "abc", String.class);
    }

    // A declared gzip encoding over plain bytes: Apache HC5's decoder fails on the first read.
    private void assertContentEncodingMismatchIsBadGateway(String contentType, String plainBody, Class<?> type) {
        wm.stubFor(get(urlEqualTo("/not-gzip")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", contentType)
                .withHeader("Content-Encoding", "gzip")
                .withBody(plainBody)));

        ResponseHandler<?> rh = httpRequest.target(uri("/not-gzip")).get(type);

        assertPayloadFailure(rh);
        Throwable cause = assertThrows(ResponseException.class, rh::orElseThrow).getCause();
        assertInstanceOf(ZipException.class, cause.getCause());
    }

    private static void assertTransportFailure(ResponseHandler<?> rh, Class<? extends IOException> expectedCause) {
        assertEquals(503, rh.getCode(), "A body stream that breaks off must surface as SERVICE_UNAVAILABLE");
        assertEquals(200, rh.getOriginalCode());
        Throwable cause = assertThrows(ResponseException.class, rh::orElseThrow).getCause();
        assertInstanceOf(expectedCause, cause);
        assertFalse(cause instanceof ResponseBodyReaderException,
                "A transport failure must not be reported as a deserialization failure, got: " + cause);
    }

    private static void assertPayloadFailure(ResponseHandler<?> rh) {
        assertEquals(502, rh.getCode(), "An unusable payload must surface as BAD_GATEWAY");
        assertEquals(200, rh.getOriginalCode());
        Throwable cause = assertThrows(ResponseException.class, rh::orElseThrow).getCause();
        assertInstanceOf(ResponseBodyReaderException.class, cause);
        assertFalse(cause instanceof InvalidContentLengthException);
    }

    private static byte[] utf32(int codePoint) {
        return ByteBuffer.allocate(4).putInt(codePoint).array();
    }

    private String uri(String path) {
        return wm.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    /**
     * Serves one request: a {@code 200} declaring a 1000-byte body, of which only
     * {@code partialBody} is sent; then the connection is closed — after {@code stallMillis}, so
     * a positive value makes the client's read time out first.
     */
    private String brokenServer(String contentType, String partialBody, long stallMillis) throws IOException {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        brokenServers.add(server);
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                skipRequestHead(socket.getInputStream());
                String head = "HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\nContent-Length: 1000\r\n\r\n";
                socket.getOutputStream().write((head + partialBody).getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                Thread.sleep(stallMillis);
            } catch (IOException | InterruptedException ignored) {
                // The client may hang up first; the server's only job is to break off mid-body.
            }
        }, "broken-body-server");
        thread.setDaemon(true);
        thread.start();
        return "http://127.0.0.1:" + server.getLocalPort() + "/";
    }

    private static void skipRequestHead(InputStream in) throws IOException {
        int matched = 0;
        byte[] terminator = {'\r', '\n', '\r', '\n'};
        int b;
        while (matched < terminator.length && (b = in.read()) != -1) {
            matched = b == terminator[matched] ? matched + 1 : (b == '\r' ? 1 : 0);
        }
    }

    static class DummyData {
        public String data;
    }
}
