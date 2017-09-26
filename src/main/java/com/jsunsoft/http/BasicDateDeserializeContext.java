/*
 * Copyright 2017 Benik Arakelyan
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

public class BasicDateDeserializeContext implements DateDeserializeContext {
    static final DateDeserializeContext DEFAULT = new BasicDateDeserializeContext("dd/MM/yyyy", "HH:mm:ss", "dd/MM/yyyy HH:mm:ss");

    private final String datePattern;
    private final String timePattern;
    private final String dateTimePattern;

    public BasicDateDeserializeContext(String datePattern, String timePattern, String dateTimePattern) {
        this.datePattern = ArgsCheck.notNull(datePattern, "datePattern");
        this.timePattern = ArgsCheck.notNull(timePattern, "timePattern");
        this.dateTimePattern = ArgsCheck.notNull(dateTimePattern, "dateTimePattern");
    }

    public String getDatePattern() {
        return datePattern;
    }

    public String getTimePattern() {
        return timePattern;
    }

    public String getDateTimePattern() {
        return dateTimePattern;
    }
}
