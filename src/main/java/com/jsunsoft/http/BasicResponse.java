/*
 * Copyright (c) 2021. Benik Arakelyan
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.*;

class BasicResponse implements Response {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicResponse.class);

    /**
     * Runs close-drains so the caller's {@code close()} can abandon them at the configured
     * {@link ResponseBodyReaderConfig#getResponseDrainTimeout() drain timeout} — the same design
     * as the JDK's {@code KeepAliveStreamCleaner} (which drains up to 512 KB on a background
     * thread to keep connections reusable). Abandoning a drain immediately detaches the entity
     * and closes the connection, which makes the drain thread's blocked read fail fast and the
     * thread exit — nothing lingers beyond the socket teardown. The abandon/EOF race is safe:
     * HC5's {@code InternalExecRuntime} clears its endpoint reference atomically, so whichever
     * side acts second (the drain releasing at EOF vs. the caller discarding on timeout) finds
     * the reference gone and no-ops — a connection released to the pool a moment after the
     * timeout is genuinely reusable and is never double-freed. Daemon threads; idle threads die
     * after the cached-pool keep-alive, so the pool costs nothing when no size-capped responses
     * are closed.
     */
    private static final ExecutorService CLOSE_DRAIN_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "http-request-close-drain");
        thread.setDaemon(true);
        return thread;
    });

    private final ClassicHttpResponse classicHttpResponse;
    private final ResponseBodyReaderConfig responseBodyReaderConfig;
    private final URI uri;
    private boolean closed;

    public BasicResponse(ClassicHttpResponse classicHttpResponse, ResponseBodyReaderConfig responseBodyReaderConfig, URI uri) {
        this.classicHttpResponse = classicHttpResponse;
        this.responseBodyReaderConfig = responseBodyReaderConfig;
        this.uri = uri;

        HttpEntity entity = classicHttpResponse.getEntity();
        if (entity != null && responseBodyReaderConfig.getMaxResponseBodySizeBytes() > 0) {
            classicHttpResponse.setEntity(new BoundedHttpEntity(entity, responseBodyReaderConfig.getMaxResponseBodySizeBytes()));
        }
    }

    ResponseBodyReaderConfig getResponseBodyReaderConfig() {
        return responseBodyReaderConfig;
    }

    /**
     * Best-effort close of the response.
     * <p>
     * Without a body-size cap the unread remainder is fully consumed so the pooled connection
     * stays reusable. With a cap configured the remainder is drained through the cap-enforcing
     * bounded stream under a dual bound — at most
     * {@link ResponseBodyReaderConfig#getMaxResponseDrainSizeBytes()} of data within at most
     * {@link ResponseBodyReaderConfig#getResponseDrainTimeout()} of wall-clock time: reaching EOF
     * within both keeps the connection reusable; otherwise (slow link, over-allowance, over-cap,
     * or declared-over-cap) the remainder is not read any further — the entity is detached and
     * the connection is discarded instead. The detach is load-bearing: Apache HC5's own
     * {@code response.close()} self-exhausts the entity's <em>raw</em> stream (bypassing the
     * bounded wrapper), so without it a hostile oversized body would be read to the end on
     * close despite the cap.
     * <p>
     * Never throws the cap exception — drain failures only downgrade connection reuse.
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            // Idempotent: the remainder was already handled on the first close (reachable e.g.
            // through RetryableWebTarget's exception-safety net) — don't consume or drain again.
            classicHttpResponse.close();
            return;
        }
        closed = true;

        try {
            HttpEntity entity = getEntity();
            if (entity != null) {
                if (responseBodyReaderConfig.getMaxResponseBodySizeBytes() <= 0) {
                    EntityUtils.consumeQuietly(entity);
                } else if (!drainedToEofWithinBounds(entity)) {
                    classicHttpResponse.setEntity(null);
                }
            }
        } finally {
            classicHttpResponse.close();
        }
    }

    /**
     * Drains the unread remainder through the cap-enforcing stream, bounded by the configured
     * drain size and drain timeout. The byte bound runs inside the drain task; the time bound is
     * enforced by abandoning the task — a read blocked on a slow peer cannot be interrupted from
     * within. An abandoned drain's blocked read dies as soon as the caller detaches the entity
     * and the connection is closed underneath it.
     *
     * @return {@code true} when EOF was reached in time (connection may be reused); {@code false}
     * when a bound ran out, draining is disabled, the cap was exceeded, or the body could not be
     * read — the caller must then prevent further reading and let the connection be discarded.
     */
    private boolean drainedToEofWithinBounds(HttpEntity entity) {
        if (!entity.isStreaming()) {
            return true;
        }
        long maxDrainSizeBytes = responseBodyReaderConfig.getMaxResponseDrainSizeBytes();
        if (maxDrainSizeBytes == 0) {
            LOGGER.debug("Close-drain is disabled (drain size 0); discarding the connection. URI: [{}]", uri);
            return false;
        }
        // TimeUnit.convert saturates instead of throwing on overflow (Duration#toNanos throws
        // ArithmeticException beyond ~292 years); computed before submit so nothing can fail
        // between scheduling the task and awaiting it.
        long drainTimeoutNanos = TimeUnit.NANOSECONDS.convert(responseBodyReaderConfig.getResponseDrainTimeout());
        Future<Boolean> drain = CLOSE_DRAIN_EXECUTOR.submit(() -> drainToEofWithinByteAllowance(entity, maxDrainSizeBytes));
        try {
            return drain.get(drainTimeoutNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            LOGGER.debug("Unread response body not drained within {} ms; discarding the connection. URI: [{}]",
                    TimeUnit.NANOSECONDS.toMillis(drainTimeoutNanos), uri);
            drain.cancel(true);
            return false;
        } catch (ExecutionException e) {
            LOGGER.debug("Close-drain of the response body failed; discarding the connection. URI: [{}]", uri, e.getCause());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            drain.cancel(true);
            return false;
        }
    }

    private boolean drainToEofWithinByteAllowance(HttpEntity entity, long maxDrainSizeBytes) {
        try {
            InputStream content = entity.getContent();
            if (content == null) {
                return true;
            }
            if (maxDrainSizeBytes < 0 || maxDrainSizeBytes == Long.MAX_VALUE) {
                // Unlimited: bounded only by the response-size cap enforced inside the stream
                // (and by the drain timeout enforced by the caller).
                IOUtils.consume(content);
                return true;
            }
            // maxCount = allowance + 1: a remainder of exactly the allowance drains to a true
            // EOF and is salvaged; only the byte beyond it trips the bound — the same probe
            // pattern (and rationale) as BoundedHttpEntity#getContent.
            BoundedInputStream allowanceBounded = BoundedInputStream.builder()
                    .setInputStream(content)
                    .setMaxCount(maxDrainSizeBytes + 1)
                    .setPropagateClose(false)
                    .get();
            IOUtils.consume(allowanceBounded);
            if (allowanceBounded.getCount() <= maxDrainSizeBytes) {
                return true;
            }
            LOGGER.debug("Unread response body exceeds the close-drain allowance of {} bytes; discarding the connection. URI: [{}]",
                    maxDrainSizeBytes, uri);
            return false;
        } catch (IOException | RuntimeException e) {
            // Includes InvalidContentLengthException from the cap-enforcing stream (cap exceeded
            // or declared-over-cap fail-fast), and the read failure of an abandoned drain whose
            // connection was closed underneath it — close() must never surface these.
            LOGGER.debug("Skipping close-drain of the response body; discarding the connection. URI: [{}]", uri, e);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCode() {
        return classicHttpResponse.getCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCode(int code) throws IllegalStateException {
        classicHttpResponse.setCode(code);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getReasonPhrase() {
        return classicHttpResponse.getReasonPhrase();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReasonPhrase(String reason) throws IllegalStateException {
        classicHttpResponse.setReasonPhrase(reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpEntity getEntity() {
        return classicHttpResponse.getEntity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEntity(HttpEntity entity) {
        classicHttpResponse.setEntity(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale() {
        return classicHttpResponse.getLocale();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLocale(Locale loc) {
        classicHttpResponse.setLocale(loc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProtocolVersion getVersion() {
        return classicHttpResponse.getVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVersion(ProtocolVersion version) {
        classicHttpResponse.setVersion(version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsHeader(String name) {
        return classicHttpResponse.containsHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countHeaders(String name) {
        return classicHttpResponse.countHeaders(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header getHeader(String name) throws ProtocolException {
        return classicHttpResponse.getHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header[] getHeaders(String name) {
        return classicHttpResponse.getHeaders(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header getFirstHeader(String name) {
        return classicHttpResponse.getFirstHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header getLastHeader(String name) {
        return classicHttpResponse.getLastHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header[] getHeaders() {
        return classicHttpResponse.getHeaders();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(Header header) {
        classicHttpResponse.addHeader(header);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(String name, Object value) {
        classicHttpResponse.addHeader(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(Header header) {
        classicHttpResponse.setHeader(header);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(String name, Object value) {
        classicHttpResponse.setHeader(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeaders(Header... headers) {
        classicHttpResponse.setHeaders(headers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeHeader(Header header) {
        return classicHttpResponse.removeHeader(header);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeHeaders(String name) {
        return classicHttpResponse.removeHeaders(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Header> headerIterator() {
        return classicHttpResponse.headerIterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Header> headerIterator(String name) {
        return classicHttpResponse.headerIterator(name);
    }

    @Override
    public <T> T readEntity(Class<T> responseType) {
        return readEntityUnChecked(responseType, responseType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readEntity(TypeReference<T> responseType) {
        return (T) readEntityUnChecked(responseType.getRawType(), responseType.getType());
    }

    @Override
    public <T> T readEntityChecked(Class<T> responseType) throws IOException {
        return readEntityChecked(responseType, responseType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readEntityChecked(TypeReference<T> responseType) throws IOException {
        return (T) readEntityChecked(responseType.getRawType(), responseType.getType());
    }

    private <T> T readEntityUnChecked(Class<T> type, Type genericType) {
        try {
            return readEntityChecked(type, genericType);
        } catch (ResponseBodyReaderException e) {
            throw new ResponseBodyProcessingException("Response deserialization failed. Cannot deserialize response to: [" + type + "].", e);
        } catch (IOException e) {
            throw new ResponseBodyProcessingException("Stream could not be created. Uri: [" + getURI() + "].", e);
        }
    }


    /**
     * Read the entity input stream as an instance of specified Java type using a {@link ResponseBodyReader}.
     * <p>
     * <p>
     * Note: method will throw any unchecked exception which will occurred in specified {@link ResponseBodyReader}.
     *
     * @param type        Java type the response entity will be converted to.
     * @param genericType Java type the response entity will be converted to.
     * @param <T>         response entity type which must match to the responseType.
     * @return Response entity
     * @throws IOException                 If the stream could not be created or error occurs reading the input stream.
     * @throws ResponseBodyReaderException If Cannot deserialize content
     */
    @SuppressWarnings("unchecked")
    private <T> T readEntityChecked(Class<T> type, Type genericType) throws IOException {
        ResponseBodyReaderContext<T> responseBodyReaderContext = new BasicResponseBodyReaderContext<>(
                this, type, genericType, getURI(),
                responseBodyReaderConfig.getMaxResponseBodySizeBytes(),
                responseBodyReaderConfig.getDefaultResponseCharset());

        for (ResponseBodyReader<?> reader : responseBodyReaderConfig.getResponseBodyReaders()) {
            if (reader.isReadable(responseBodyReaderContext)) {
                return ((ResponseBodyReader<T>) reader).read(responseBodyReaderContext);
            }
        }

        if (responseBodyReaderConfig.isUseDefaultReader()) {
            for (ResponseBodyReader<?> reader : responseBodyReaderConfig.getDefaultResponseBodyReaders()) {
                if (reader.isReadable(responseBodyReaderContext)) {
                    return ((ResponseBodyReader<T>) reader).read(responseBodyReaderContext);
                }
            }
        }

        if (hasEntity()) {
            throw new ResponseBodyReaderNotFoundException(
                    "Can't find body reader for type: " + responseBodyReaderContext.getType() + " and content type: " + responseBodyReaderContext.getContentType()
            );
        } else {
            LOGGER.warn("Can't find body reader for type: {} when http entity is null.", responseBodyReaderContext.getType());
        }

        return null;
    }

    @Override
    public URI getURI() {
        return uri;
    }
}
