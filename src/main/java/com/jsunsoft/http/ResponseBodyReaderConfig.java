/*
 * Copyright (c) 2024. Benik Arakelyan
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

/*
 * Date: 11/20/2019.
 * Developer: Beno Arakelyan
 */


import tools.jackson.databind.ObjectMapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

class ResponseBodyReaderConfig {

    /**
     * Default byte bound of the close-drain. Bounds the drain on fast links, where the time
     * bound alone would permit reading megabytes of hostile garbage. Deliberately below the
     * general-purpose defaults of the JDK (512 KB, {@code http.KeepAlive.remainingData}) and
     * .NET (1 MB, {@code MaxResponseDrainSize}) — the drain exists inside a security-cap
     * feature, so reading less unwanted data is the point.
     */
    static final long DEFAULT_MAX_RESPONSE_DRAIN_SIZE_BYTES = 64 * 1024;

    /**
     * Default wall-clock bound of the close-drain. A drain is only worth doing while it is
     * cheaper than establishing a new connection — OkHttp's discard timeout is the same 100 ms,
     * documented as "significantly less than the time it takes to establish a new connection";
     * .NET's equivalent is {@code ResponseDrainTimeout} (2 s).
     */
    static final Duration DEFAULT_RESPONSE_DRAIN_TIMEOUT = Duration.ofMillis(100);

    private final ObjectMapper defaultJsonMapper;
    private final ObjectMapper defaultXmlMapper;
    private final Collection<ResponseBodyReader<?>> responseBodyReaders;
    private final Collection<ResponseBodyReader<?>> defaultResponseBodyReaders;
    private final boolean useDefaultReader;
    /**
     * Maximum allowed response body size in bytes. {@code <= 0} means "unlimited".
     */
    private final long maxResponseBodySizeBytes;
    /**
     * Charset used to decode response bodies into {@code String} when the {@code Content-Type}
     * header carries no {@code charset} parameter. Never {@code null}.
     */
    private final Charset defaultResponseCharset;
    /**
     * Byte bound of the close-drain. {@code 0} = never drain; negative = unlimited (bounded only
     * by the response-size cap and the drain timeout). Only meaningful when
     * {@link #maxResponseBodySizeBytes} is positive.
     */
    private final long maxResponseDrainSizeBytes;
    /**
     * Wall-clock bound of the close-drain. Always positive; never {@code null}.
     */
    private final Duration responseDrainTimeout;

    private ResponseBodyReaderConfig(ObjectMapper defaultJsonMapper,
                                     ObjectMapper defaultXmlMapper,
                                     Collection<ResponseBodyReader<?>> responseBodyReaders,
                                     Collection<ResponseBodyReader<?>> defaultResponseBodyReaders,
                                     boolean useDefaultReader,
                                     long maxResponseBodySizeBytes,
                                     Charset defaultResponseCharset,
                                     long maxResponseDrainSizeBytes,
                                     Duration responseDrainTimeout) {
        this.defaultJsonMapper = defaultJsonMapper;
        this.defaultXmlMapper = defaultXmlMapper;
        this.responseBodyReaders = List.copyOf(ArgsCheck.notNull(responseBodyReaders, "responseBodyReaders"));
        this.defaultResponseBodyReaders = List.copyOf(ArgsCheck.notNull(defaultResponseBodyReaders, "defaultResponseBodyReaders"));
        this.useDefaultReader = useDefaultReader;
        this.maxResponseBodySizeBytes = maxResponseBodySizeBytes;
        this.defaultResponseCharset = ArgsCheck.notNull(defaultResponseCharset, "defaultResponseCharset");
        this.maxResponseDrainSizeBytes = maxResponseDrainSizeBytes;
        this.responseDrainTimeout = ArgsCheck.notNull(responseDrainTimeout, "responseDrainTimeout");
    }

    static Builder create() {
        return new Builder();
    }

    public ObjectMapper getDefaultJsonMapper() {
        return defaultJsonMapper;
    }

    public ObjectMapper getDefaultXmlMapper() {
        return defaultXmlMapper;
    }

    Collection<ResponseBodyReader<?>> getDefaultResponseBodyReaders() {
        return defaultResponseBodyReaders;
    }

    Collection<ResponseBodyReader<?>> getResponseBodyReaders() {
        return responseBodyReaders;
    }

    boolean isUseDefaultReader() {
        return useDefaultReader;
    }

    long getMaxResponseBodySizeBytes() {
        return maxResponseBodySizeBytes;
    }

    Charset getDefaultResponseCharset() {
        return defaultResponseCharset;
    }

    long getMaxResponseDrainSizeBytes() {
        return maxResponseDrainSizeBytes;
    }

    Duration getResponseDrainTimeout() {
        return responseDrainTimeout;
    }

    static class Builder {
        private Collection<ResponseBodyReader<?>> responseBodyReaders;
        private boolean useDefaultReader = true;

        private ObjectMapper defaultJsonMapper;
        private ObjectMapper defaultXmlMapper;

        private Map<Class<?>, String> dateTypeToPattern;
        private long maxResponseBodySizeBytes;
        private Charset defaultResponseCharset = StandardCharsets.UTF_8;
        // null = not explicitly configured → default applies; tracked so build() can reject
        // drain options configured without the response-size cap they belong to.
        private Long maxResponseDrainSizeBytes;
        private Duration responseDrainTimeout;

        private Builder() {
        }

        Builder setMaxResponseBodySizeBytes(long maxResponseBodySizeBytes) {
            this.maxResponseBodySizeBytes = maxResponseBodySizeBytes;
            return this;
        }

        Builder setMaxResponseDrainSizeBytes(long maxResponseDrainSizeBytes) {
            this.maxResponseDrainSizeBytes = maxResponseDrainSizeBytes;
            return this;
        }

        Builder setResponseDrainTimeout(Duration responseDrainTimeout) {
            this.responseDrainTimeout = ArgsCheck.notNull(responseDrainTimeout, "responseDrainTimeout");
            return this;
        }

        Builder setDefaultResponseCharset(Charset defaultResponseCharset) {
            this.defaultResponseCharset = ArgsCheck.notNull(defaultResponseCharset, "defaultResponseCharset");
            return this;
        }

        Builder addResponseBodyReader(ResponseBodyReader<?> responseBodyReader) {
            if (responseBodyReaders == null) {
                responseBodyReaders = new LinkedHashSet<>();
            }

            this.responseBodyReaders.add(responseBodyReader);

            return this;
        }

        Builder setUseDefaultBodyReader(boolean useDefaultReader) {
            this.useDefaultReader = useDefaultReader;

            return this;
        }

        Builder addDateDeserializationPattern(Class<?> dateType, String pattern) {
            if (dateTypeToPattern == null) {
                dateTypeToPattern = new HashMap<>();
            }

            dateTypeToPattern.put(dateType, pattern);

            return this;
        }

        public Builder setDefaultJsonMapper(ObjectMapper defaultJsonMapper) {
            this.defaultJsonMapper = defaultJsonMapper;

            return this;
        }

        public Builder setDefaultXmlMapper(ObjectMapper defaultXmlMapper) {
            this.defaultXmlMapper = defaultXmlMapper;

            return this;
        }

        ResponseBodyReaderConfig build() {
            ObjectMapper json = null;
            ObjectMapper xml = null;
            Collection<ResponseBodyReader<?>> defaultResponseBodyReaders = Collections.emptyList();

            if (useDefaultReader) {
                json = ObjectMapperInitializer.initJsonMapperIfNull(defaultJsonMapper, dateTypeToPattern);
                xml = ObjectMapperInitializer.initXmlMapperIfNull(defaultXmlMapper, dateTypeToPattern);
                defaultResponseBodyReaders = List.of(
                        ResponseBodyReaders.stringReader(),
                        ResponseBodyReaders.byteReader(),
                        ResponseBodyReaders.jsonReader(json),
                        ResponseBodyReaders.xmlReader(xml));
            } else {
                if (defaultJsonMapper != null || defaultXmlMapper != null || dateTypeToPattern != null) {
                    throw new IllegalArgumentException("Do not provide defaultJsonMapper/defaultXmlMapper/dateTypeToPattern if default body reader is disabled.");
                }
            }

            if ((maxResponseDrainSizeBytes != null || responseDrainTimeout != null) && maxResponseBodySizeBytes <= 0) {
                throw new IllegalArgumentException("Response drain options apply only when a response body size cap is configured — call setMaxResponseBodySizeBytes(...) with a positive value.");
            }

            // Resolved into locals — assigning defaults back into the fields would break
            // build → mutate → build reuse.
            Collection<ResponseBodyReader<?>> effectiveResponseBodyReaders =
                    responseBodyReaders != null ? responseBodyReaders : Collections.emptyList();
            long effectiveMaxResponseDrainSizeBytes =
                    maxResponseDrainSizeBytes != null ? maxResponseDrainSizeBytes : DEFAULT_MAX_RESPONSE_DRAIN_SIZE_BYTES;
            Duration effectiveResponseDrainTimeout =
                    responseDrainTimeout != null ? responseDrainTimeout : DEFAULT_RESPONSE_DRAIN_TIMEOUT;

            return new ResponseBodyReaderConfig(json, xml, effectiveResponseBodyReaders, defaultResponseBodyReaders, useDefaultReader, maxResponseBodySizeBytes, defaultResponseCharset, effectiveMaxResponseDrainSizeBytes, effectiveResponseDrainTimeout);
        }
    }
}
