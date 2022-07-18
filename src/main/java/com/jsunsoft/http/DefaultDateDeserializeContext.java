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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class DefaultDateDeserializeContext implements DateDeserializeContext {
    static final DateDeserializeContext DEFAULT = new DefaultDateDeserializeContext();

    private static final Map<Class<?>, String> DEFAULT_DATE_TYPE_TO_PATTERN;

    static {
        Map<Class<?>, String> dateTypeToPattern = new HashMap<>();

        dateTypeToPattern.put(LocalTime.class, "HH:mm:ss");
        dateTypeToPattern.put(LocalDate.class, "dd/MM/yyyy");
        dateTypeToPattern.put(LocalDateTime.class, "dd/MM/yyyy HH:mm:ss");

        dateTypeToPattern.put(org.joda.time.LocalTime.class, "HH:mm:ss");
        dateTypeToPattern.put(org.joda.time.LocalDate.class, "dd/MM/yyyy");
        dateTypeToPattern.put(org.joda.time.LocalDateTime.class, "dd/MM/yyyy HH:mm:ss");

        DEFAULT_DATE_TYPE_TO_PATTERN = Collections.unmodifiableMap(dateTypeToPattern);
    }

    DefaultDateDeserializeContext() {

    }

    @Override
    public Map<Class<?>, String> getDateTypeToPattern() {
        return DEFAULT_DATE_TYPE_TO_PATTERN;
    }
}
