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

import java.util.HashMap;
import java.util.Map;

class RequestBodySerializeConfig {
    private final ObjectMapper defaultJsonMapper;
    private final ObjectMapper defaultXmlMapper;

    RequestBodySerializeConfig(ObjectMapper defaultJsonMapper, ObjectMapper defaultXmlMapper) {
        this.defaultJsonMapper = defaultJsonMapper;
        this.defaultXmlMapper = defaultXmlMapper;
    }

    public ObjectMapper getDefaultJsonMapper() {
        return defaultJsonMapper;
    }

    public ObjectMapper getDefaultXmlMapper() {
        return defaultXmlMapper;
    }

    static Builder create() {
        return new Builder();
    }

    static class Builder {

        private ObjectMapper defaultJsonMapper;
        private ObjectMapper defaultXmlMapper;

        private Map<Class<?>, String> dateTypeToPattern;

        private Builder() {
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

        RequestBodySerializeConfig build() {

            ObjectMapper json = ObjectMapperInitializer.initJsonMapperIfNull(defaultJsonMapper, dateTypeToPattern);
            ObjectMapper xml = ObjectMapperInitializer.initXmlMapperIfNull(defaultXmlMapper, dateTypeToPattern);


            return new RequestBodySerializeConfig(json, xml);
        }
    }
}
