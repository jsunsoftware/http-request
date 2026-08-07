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

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class BoundedHttpEntity extends HttpEntityWrapper {

    private final long maxSize;
    private InputStream boundedContent;

    BoundedHttpEntity(final HttpEntity entity, final long maxSize) {
        super(entity);
        this.maxSize = maxSize;
    }

    /**
     * Reports {@code min(actual, maxSize)} — deliberately, even though a declared length beyond
     * the cap is rejected on {@link #getContent()} anyway: consumers commonly pre-allocate
     * buffers sized by {@code getContentLength()} <em>before</em> touching the content, and
     * advertising a hostile multi-GB declared length would hand them an allocation-DoS. The
     * capped value can never mislead a reader into silent truncation because any attempt to
     * actually read a declared-over-cap body fails fast in {@link #getContent()}.
     */
    @Override
    public long getContentLength() {
        long wrapped = super.getContentLength();
        if (wrapped < 0) {
            return -1;
        }
        return Math.min(wrapped, maxSize);
    }

    /**
     * Wraps the underlying entity stream so that no more than {@code maxSize} bytes can be read
     * before the library throws {@link InvalidContentLengthException}.
     * <p>
     * <b>Fail-fast on the declared length:</b> when the response declares a {@code Content-Length}
     * beyond the cap, this method throws before reading a single byte. Besides being cheaper than
     * streaming up to the cap, this closes the silent-truncation trap: a consumer that trusts
     * {@link #getContentLength()} (which reports the capped value) would otherwise read exactly
     * that many bytes, stop before the streaming bound fires, and treat the truncated body as
     * complete.
     * <p>
     * <b>One stream, one budget:</b> the bounded stream is created once and returned for every
     * call. That is required by the {@link HttpEntity#getContent()} contract for non-repeatable
     * entities (the same stream instance must be returned), and it makes the byte budget a
     * per-entity budget — handing out a fresh wrapper per call would reset the count and let a
     * caller read {@code n × maxSize} bytes through repeated {@code getContent()} calls.
     * <p>
     * The cap is delegated to commons-io's {@link BoundedInputStream}, which clamps both
     * {@code read(byte[], int, int)} and {@code skip(long)} so wire reads cannot exceed the
     * configured budget. We register an {@code onMaxCount} handler that throws
     * {@link InvalidContentLengthException} the moment the bound is hit, instead of accepting
     * the upstream default of "silently return EOF at the limit."
     * <p>
     * <b>Why {@code maxSize + 1}, not {@code maxSize}:</b> {@code BoundedInputStream} fires
     * {@code onMaxCount} as soon as {@code count >= maxCount} — i.e., at the start of the read
     * after consuming the last allowed byte. With {@code maxCount = maxSize}, a body of exactly
     * {@code maxSize} bytes (which the user explicitly said is acceptable) would consume the
     * full body cleanly but then throw on the next probe-for-EOF read. Setting
     * {@code maxCount = maxSize + 1} shifts the trigger to the byte that <em>exceeds</em> the
     * user's cap: bodies of size {@code <= maxSize} drain to EOF without throwing, bodies of
     * size {@code > maxSize} throw. <b>Do not "simplify" this to {@code setMaxCount(maxSize)}</b>
     * — that would reject responses the contract says are acceptable. The boundary is regression-
     * tested in {@code SizeLimitTest}.
     */
    @Override
    public InputStream getContent() throws IOException {
        long declaredLength = super.getContentLength();
        if (declaredLength > maxSize) {
            throw new InvalidContentLengthException(declaredLength,
                    "Declared Content-Length exceeds maximum allowed size: " + maxSize + " bytes");
        }
        if (boundedContent == null) {
            boundedContent = BoundedInputStream.builder()
                    .setInputStream(super.getContent())
                    .setMaxCount(maxSize + 1L)
                    .setOnMaxCount((max, count) -> {
                        throw new InvalidContentLengthException(count,
                                "Response body exceeds maximum allowed size: " + maxSize + " bytes");
                    })
                    .setPropagateClose(true)
                    .get();
        }
        return boundedContent;
    }

    /**
     * Writes the bounded content to {@code outStream}.
     * <p>
     * <b>This override is load-bearing — do not remove it.</b> {@link HttpEntityWrapper#writeTo}
     * delegates straight to the wrapped entity's {@code writeTo}, which would bypass our
     * {@link BoundedInputStream} entirely and let an oversize body stream out unchecked. By
     * routing through {@link #getContent()} we guarantee the size cap is enforced on every
     * read, regardless of which API the caller uses (e.g. {@code response.getEntity().writeTo(...)}
     * to spool a response to disk).
     */
    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        ArgsCheck.notNull(outStream, "Output stream");

        try (InputStream inStream = getContent()) {
            inStream.transferTo(outStream);
        }
    }
}
