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

/**
 * Configured connection parameters.
 * <p>
 * ConnectionConfig objects are not immutable they can't be shared.
 */
public class ConnectionConfig {
    private int socketTimeOut = 30000;
    private int connectTimeout = 5000;
    private int connectionRequestTimeout = 30000;
    private int maxPoolSize = 128;

    private ConnectionConfig() {
    }

    /**
     * @param connectTimeout The Connection Timeout (http.connection.timeout) – the time to establish the connection with the remote host.
     *                       By default 5000ms
     * @return Builder instance
     */
    public ConnectionConfig connectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    /**
     * @param socketTimeOut The Socket Timeout (http.socket.timeout) – the time waiting for data – after the connection was established;
     *                      maximum time of inactivity between two data packets. By default 30000ms
     * @return Builder instance
     */
    public ConnectionConfig socketTimeOut(int socketTimeOut) {
        this.socketTimeOut = socketTimeOut;
        return this;
    }

    /**
     * @param connectionRequestTimeout The Connection Manager Timeout (http.connection-manager.timeout) –
     *                                 the time to wait for a connection from the connection manager/pool.
     *                                 By default 30000ms
     * @return Builder instance
     */
    public ConnectionConfig connectionRequestTimeout(int connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
        return this;
    }

    /**
     * Set the connection pool max size of concurrent connections to a specific route, which is 128 by default.
     *
     * @param maxPoolSize value
     * @return Builder instance
     */
    public ConnectionConfig maxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    public static ConnectionConfig create() {
        return new ConnectionConfig();
    }

    int getSocketTimeOut() {
        return socketTimeOut;
    }

    int getConnectTimeout() {
        return connectTimeout;
    }

    int getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    int getMaxPoolSize() {
        return maxPoolSize;
    }
}
