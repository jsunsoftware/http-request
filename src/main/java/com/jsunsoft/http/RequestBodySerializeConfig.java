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


import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

class RequestBodySerializeConfig {
    private final ObjectMapper defaultJsonMapper;
    private final ObjectMapper defaultXmlMapper;
    private final Collection<RequestBodyConverter> requestBodyConverters;
    private final Collection<RequestBodyConverter> defaultRequestBodyConverters;
    private final boolean useDefaultBodySerializer;

    private RequestBodySerializeConfig(ObjectMapper defaultJsonMapper,
                                       ObjectMapper defaultXmlMapper,
                                       Collection<RequestBodyConverter> requestBodyConverters,
                                       Collection<RequestBodyConverter> defaultRequestBodyConverters,
                                       boolean useDefaultBodySerializer) {
        this.defaultJsonMapper = defaultJsonMapper;
        this.defaultXmlMapper = defaultXmlMapper;
        this.requestBodyConverters = List.copyOf(ArgsCheck.notNull(requestBodyConverters, "requestBodyConverters"));
        this.defaultRequestBodyConverters = List.copyOf(ArgsCheck.notNull(defaultRequestBodyConverters, "defaultRequestBodyConverters"));
        this.useDefaultBodySerializer = useDefaultBodySerializer;
    }

    public ObjectMapper getDefaultJsonMapper() {
        return defaultJsonMapper;
    }

    public ObjectMapper getDefaultXmlMapper() {
        return defaultXmlMapper;
    }

    Collection<RequestBodyConverter> getRequestBodyConverters() {
        return requestBodyConverters;
    }

    Collection<RequestBodyConverter> getDefaultRequestBodyConverters() {
        return defaultRequestBodyConverters;
    }

    boolean isUseDefaultBodySerializer() {
        return useDefaultBodySerializer;
    }

    static Builder create() {
        return new Builder();
    }

    static class Builder {

        private ObjectMapper defaultJsonMapper;
        private ObjectMapper defaultXmlMapper;

        private Map<Class<?>, String> dateTypeToPattern;
        private Collection<RequestBodyConverter> requestBodyConverters;
        private boolean useDefaultBodySerializer = true;

        private Builder() {
        }

        Builder addDateDeserializationPattern(Class<?> dateType, String pattern) {
            if (dateTypeToPattern == null) {
                dateTypeToPattern = new HashMap<>();
            }

            dateTypeToPattern.put(dateType, pattern);

            return this;
        }

        Builder addRequestBodyConverter(RequestBodyConverter requestBodyConverter) {
            if (requestBodyConverters == null) {
                requestBodyConverters = new LinkedHashSet<>();
            }
            requestBodyConverters.add(requestBodyConverter);
            return this;
        }

        Builder setUseDefaultBodySerializer(boolean useDefaultBodySerializer) {
            this.useDefaultBodySerializer = useDefaultBodySerializer;
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

        RequestBodySerializeConfig build() {

            ObjectMapper json = null;
            ObjectMapper xml = null;
            Collection<RequestBodyConverter> defaultRequestBodyConverters = Collections.emptyList();

            if (useDefaultBodySerializer) {
                json = ObjectMapperInitializer.initJsonMapperIfNull(defaultJsonMapper, dateTypeToPattern);
                xml = ObjectMapperInitializer.initXmlMapperIfNull(defaultXmlMapper, dateTypeToPattern);
                defaultRequestBodyConverters = List.of(
                        RequestBodyConverters.jsonConverter(json),
                        RequestBodyConverters.xmlConverter(xml));
            } else {
                if (defaultJsonMapper != null || defaultXmlMapper != null || dateTypeToPattern != null) {
                    throw new IllegalArgumentException("Do not provide defaultJsonMapper/defaultXmlMapper/dateTypeToPattern if default body serializer is disabled.");
                }
            }

            if (requestBodyConverters == null) {
                requestBodyConverters = Collections.emptyList();
            }

            return new RequestBodySerializeConfig(json, xml, requestBodyConverters, defaultRequestBodyConverters, useDefaultBodySerializer);
        }
    }
}
