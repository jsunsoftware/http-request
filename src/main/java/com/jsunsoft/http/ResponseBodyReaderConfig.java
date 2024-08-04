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

import java.util.*;

class ResponseBodyReaderConfig {
    private final ResponseBodyReader<?> defaultResponseBodyReader;
    private final Collection<ResponseBodyReader<?>> responseBodyReaders;
    private final boolean useDefaultReader;

    private ResponseBodyReaderConfig(ResponseBodyReader<?> defaultResponseBodyReader, Collection<ResponseBodyReader<?>> responseBodyReaders, boolean useDefaultReader) {
        this.defaultResponseBodyReader = defaultResponseBodyReader;
        this.responseBodyReaders = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(responseBodyReaders, "responseBodyReaders")));
        this.useDefaultReader = useDefaultReader;
    }

    static Builder create() {
        return new Builder();
    }

    ResponseBodyReader<?> getDefaultResponseBodyReader() {
        return defaultResponseBodyReader;
    }

    Collection<ResponseBodyReader<?>> getResponseBodyReaders() {
        return responseBodyReaders;
    }

    boolean isUseDefaultReader() {
        return useDefaultReader;
    }

    static class Builder {
        private ResponseBodyReader<?> defaultResponseBodyReader;
        private Collection<ResponseBodyReader<?>> responseBodyReaders;
        private boolean useDefaultReader = true;

        private ObjectMapper defaultJsonMapper;
        private ObjectMapper defaultXmlMapper;

        private Map<Class<?>, String> dateTypeToPattern;

        private Builder() {
        }

        Builder setDefaultResponseBodyReader(ResponseBodyReader<?> defaultResponseBodyReader) {
            this.defaultResponseBodyReader = defaultResponseBodyReader;
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
            if (useDefaultReader && defaultResponseBodyReader == null) {

                ObjectMapper json = ObjectMapperInitializer.initJsonMapperIfNull(defaultJsonMapper, dateTypeToPattern);
                ObjectMapper xml = ObjectMapperInitializer.initXmlMapperIfNull(defaultXmlMapper, dateTypeToPattern);

                defaultResponseBodyReader = new DefaultResponseBodyReader<>(json, xml);
            } else {
                if (defaultJsonMapper != null) {
                    throw new IllegalArgumentException("Do not provide defaultJsonMapper if default body reader is not used.");
                }

                if (defaultXmlMapper != null) {
                    throw new IllegalArgumentException("Do not provide defaultXmlMapper if default body reader is not used.");
                }

                if (dateTypeToPattern != null) {
                    throw new IllegalArgumentException("Do not provide dateTypeToPattern if default body reader is not used.");
                }
            }

            if (responseBodyReaders == null) {
                responseBodyReaders = Collections.emptyList();
            }

            return new ResponseBodyReaderConfig(defaultResponseBodyReader, responseBodyReaders, useDefaultReader);
        }
    }
}
