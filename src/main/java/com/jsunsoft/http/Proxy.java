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

import java.net.URI;

public class Proxy {
    private final String host;
    private final int port;
    private final String scheme;

    public Proxy(String host, int port, String scheme) {
        this.host = host;
        this.port = port;
        this.scheme = scheme;
    }

    public Proxy(String host, int port) {
        this(host, port, null);
    }

    public Proxy(URI uri) {
        this(uri.getHost(), uri.getPort(), uri.getScheme());
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getScheme() {
        return scheme;
    }
}
