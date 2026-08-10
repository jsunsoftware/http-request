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
import java.util.Map;

/**
 * Holds the date patterns the library installed implicitly through 4.0.0.
 *
 * @deprecated See {@link DateDeserializeContext} — since 5.0.0-rc1 the default mappers use
 * Jackson 3's ISO-8601 {@code java.time} handling and these patterns are no longer applied.
 * Scheduled for removal.
 */
@Deprecated(since = "5.0.0-rc1", forRemoval = true)
@SuppressWarnings("removal")
class DefaultDateDeserializeContext implements DateDeserializeContext {
    static final DateDeserializeContext DEFAULT = new DefaultDateDeserializeContext();

    private static final Map<Class<?>, String> DEFAULT_DATE_TYPE_TO_PATTERN = Map.of(
            LocalTime.class, "HH:mm:ss",
            LocalDate.class, "dd/MM/yyyy",
            LocalDateTime.class, "dd/MM/yyyy HH:mm:ss"
    );

    DefaultDateDeserializeContext() {

    }

    @Override
    public Map<Class<?>, String> getDateTypeToPattern() {
        return DEFAULT_DATE_TYPE_TO_PATTERN;
    }
}
