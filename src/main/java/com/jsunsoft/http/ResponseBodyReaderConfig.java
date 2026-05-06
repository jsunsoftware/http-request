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


import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

class ResponseBodyReaderConfig {
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

    private ResponseBodyReaderConfig(ObjectMapper defaultJsonMapper,
                                     ObjectMapper defaultXmlMapper,
                                     Collection<ResponseBodyReader<?>> responseBodyReaders,
                                     Collection<ResponseBodyReader<?>> defaultResponseBodyReaders,
                                     boolean useDefaultReader,
                                     long maxResponseBodySizeBytes,
                                     Charset defaultResponseCharset) {
        this.defaultJsonMapper = defaultJsonMapper;
        this.defaultXmlMapper = defaultXmlMapper;
        this.responseBodyReaders = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(responseBodyReaders, "responseBodyReaders")));
        this.defaultResponseBodyReaders = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(defaultResponseBodyReaders, "defaultResponseBodyReaders")));
        this.useDefaultReader = useDefaultReader;
        this.maxResponseBodySizeBytes = maxResponseBodySizeBytes;
        this.defaultResponseCharset = ArgsCheck.notNull(defaultResponseCharset, "defaultResponseCharset");
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

    static class Builder {
        private Collection<ResponseBodyReader<?>> responseBodyReaders;
        private boolean useDefaultReader = true;

        private ObjectMapper defaultJsonMapper;
        private ObjectMapper defaultXmlMapper;

        private Map<Class<?>, String> dateTypeToPattern;
        private long maxResponseBodySizeBytes;
        private Charset defaultResponseCharset = StandardCharsets.UTF_8;

        private Builder() {
        }

        Builder setMaxResponseBodySizeBytes(long maxResponseBodySizeBytes) {
            this.maxResponseBodySizeBytes = maxResponseBodySizeBytes;
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
                List<ResponseBodyReader<?>> defaults = new ArrayList<>(4);
                defaults.add(ResponseBodyReaders.stringReader());
                defaults.add(ResponseBodyReaders.byteReader());
                defaults.add(ResponseBodyReaders.jsonReader(json));
                defaults.add(ResponseBodyReaders.xmlReader(xml));
                defaultResponseBodyReaders = Collections.unmodifiableList(defaults);
            } else {
                if (defaultJsonMapper != null || defaultXmlMapper != null || dateTypeToPattern != null) {
                    throw new IllegalArgumentException("Do not provide defaultJsonMapper/defaultXmlMapper/dateTypeToPattern if default body reader is disabled.");
                }
            }

            if (responseBodyReaders == null) {
                responseBodyReaders = Collections.emptyList();
            }

            return new ResponseBodyReaderConfig(json, xml, responseBodyReaders, defaultResponseBodyReaders, useDefaultReader, maxResponseBodySizeBytes, defaultResponseCharset);
        }
    }
}
