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

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-level pins for {@link BoundedHttpEntity}'s cap enforcement:
 * <ul>
 *     <li>the byte budget is a single budget per entity — repeated {@code getContent()} calls
 *         must not reset it (and, per the {@code HttpEntity} contract for non-repeatable
 *         entities, must return the same stream instance);</li>
 *     <li>a declared {@code Content-Length} beyond the cap fails fast on {@code getContent()} /
 *         {@code writeTo()}, closing the silent-truncation trap where a length-trusting consumer
 *         reads exactly the advertised bytes and never trips the streaming cap.</li>
 * </ul>
 */
class BoundedHttpEntityTest {

    private static final int CAP = 10;

    @Test
    void getContent_returnsSameStream_acrossCalls() throws IOException {
        BoundedHttpEntity entity = boundedEntity("0123456789abcdefgh", CAP); // 18 bytes, no declared length

        assertSame(entity.getContent(), entity.getContent(),
                "non-repeatable entity must hand out one stream — a fresh wrapper per call resets the byte budget");
    }

    @Test
    void readBudget_isShared_acrossGetContentCalls() throws IOException {
        BoundedHttpEntity entity = boundedEntity("0123456789abcdefgh", CAP); // 18 bytes > cap

        InputStream first = entity.getContent();
        assertEquals(CAP, first.readNBytes(CAP).length, "reading up to the cap must succeed");

        InputStream second = entity.getContent();
        assertThrows(InvalidContentLengthException.class, second::readAllBytes,
                "continuing past the cap through a second getContent() must trip the shared budget, not a fresh one");
    }

    @Test
    void declaredLengthOverCap_failsFast_withoutReadingUnderlying() {
        AtomicLong bytesRead = new AtomicLong();
        InputStream counting = new FilterInputStream(new ByteArrayInputStream(new byte[100])) {
            @Override
            public int read() throws IOException {
                bytesRead.incrementAndGet();
                return super.read();
            }

            @Override
            public int read(byte @NonNull [] b, int off, int len) throws IOException {
                int n = super.read(b, off, len);
                if (n > 0) bytesRead.addAndGet(n);
                return n;
            }
        };
        BoundedHttpEntity entity = new BoundedHttpEntity(
                new BasicHttpEntity(counting, 100, ContentType.APPLICATION_OCTET_STREAM), CAP);

        InvalidContentLengthException e = assertThrows(InvalidContentLengthException.class, entity::getContent,
                "a declared Content-Length beyond the cap must be rejected before any read");
        assertEquals(100, e.getContentLength(), "the exception must carry the offending declared length");
        assertEquals(0, bytesRead.get(), "fail-fast must not read a single byte from the wire");
    }

    @Test
    void declaredLengthOverCap_writeToFailsFast_withEmptySink() {
        BoundedHttpEntity entity = new BoundedHttpEntity(
                new BasicHttpEntity(new ByteArrayInputStream(new byte[100]), 100, ContentType.APPLICATION_OCTET_STREAM), CAP);

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        assertThrows(InvalidContentLengthException.class, () -> entity.writeTo(sink));
        assertEquals(0, sink.size(), "writeTo must not spool anything when the declared length already exceeds the cap");
    }

    @Test
    void declaredLengthExactlyAtCap_readsCleanly() throws IOException {
        byte[] body = "0123456789".getBytes();
        BoundedHttpEntity entity = new BoundedHttpEntity(
                new BasicHttpEntity(new ByteArrayInputStream(body), body.length, ContentType.APPLICATION_OCTET_STREAM), CAP);

        assertArrayEquals(body, entity.getContent().readAllBytes(),
                "a declared length exactly at the cap is acceptable and must read to EOF cleanly");
    }

    private static BoundedHttpEntity boundedEntity(String content, long cap) {
        return new BoundedHttpEntity(
                new BasicHttpEntity(new ByteArrayInputStream(content.getBytes()), ContentType.APPLICATION_OCTET_STREAM), cap);
    }
}
