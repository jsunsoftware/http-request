/*
 * Copyright (c) 2026. Benik Arakelyan
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
module com.jsunsoft.http {
    requires transitive org.apache.httpcomponents.client5.httpclient5;
    requires transitive org.apache.httpcomponents.core5.httpcore5;

    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.dataformat.xml;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.module.paramnames;

    requires org.slf4j;
    requires org.apache.commons.lang3;
    requires org.apache.commons.io;

    exports com.jsunsoft.http;
    exports com.jsunsoft.http.annotations;

    // Permit Jackson deep reflection into the main package. Required so test fixtures (POJOs
    // declared as inner classes of tests in `com.jsunsoft.http`) are reachable when a test
    // runner places test classes on the modulepath via `--patch-module` (IntelliJ's default,
    // and any other runner that ignores Surefire's `useModulePath=false`). Scope is restricted
    // to the two Jackson modules we already publish as `requires transitive`, so no other
    // module gains reflective access.
    opens com.jsunsoft.http to
            com.fasterxml.jackson.databind,
            com.fasterxml.jackson.dataformat.xml;
}
