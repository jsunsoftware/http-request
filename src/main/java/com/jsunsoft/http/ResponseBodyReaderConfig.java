/*
 * Copyright (c) 2022. Benik Arakelyan
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


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import java.util.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;

class ResponseBodyReaderConfig {
    private final ResponseBodyReader<?> defaultResponseBodyReader;
    private final Collection<ResponseBodyReader<?>> responseBodyReaders;
    private final boolean useDefaultReader;

    private ResponseBodyReaderConfig(ResponseBodyReader<?> defaultResponseBodyReader, Collection<ResponseBodyReader<?>> responseBodyReaders, boolean useDefaultReader) {
        this.defaultResponseBodyReader = defaultResponseBodyReader;
        this.responseBodyReaders = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(responseBodyReaders, "responseBodyReaders")));
        this.useDefaultReader = useDefaultReader;
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

    static Builder create() {
        return new Builder();
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

                DateDeserializeContext dateDeserializeContext = dateTypeToPattern == null || dateTypeToPattern.isEmpty() ?
                        DefaultDateDeserializeContext.DEFAULT : new BasicDateDeserializeContext(dateTypeToPattern);

                ObjectMapper json = defaultJsonMapper != null ? defaultJsonMapper : defaultInit(new ObjectMapper(), dateDeserializeContext);
                ObjectMapper xml = defaultXmlMapper != null ? defaultXmlMapper : defaultInit(new XmlMapper(), dateDeserializeContext);

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

    static ObjectMapper defaultInit(ObjectMapper objectMapper, DateDeserializeContext dateDeserializeContext) {

        dateDeserializeContext.getDateTypeToPattern()
                .forEach((type, pattern) ->
                        objectMapper.configOverride(type)
                                .setFormat(
                                        JsonFormat.Value.forPattern(pattern)
                                )
                );

        objectMapper.setSerializationInclusion(NON_NULL)
                .disable(FAIL_ON_EMPTY_BEANS)
                .disable(FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .registerModules(new JodaModule(),
                        new ParameterNamesModule(JsonCreator.Mode.PROPERTIES),
                        new Jdk8Module(), new JavaTimeModule()
                );
        return objectMapper;
    }
}
