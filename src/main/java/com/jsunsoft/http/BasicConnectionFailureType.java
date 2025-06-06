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
 * Type to specified why connection is failure.
 */
enum BasicConnectionFailureType implements ConnectionFailureType {
    NONE,
    RESPONSE_TIMEOUT,
    CONNECT_TIMEOUT,
    CONNECTION_POOL_IS_EMPTY,
    SERVICE_UNREACHABLE,
    IO,
    UNDEFINED;

    public boolean isFailed() {
        return this != NONE;
    }

    public boolean isNotFailed() {
        return this == NONE;
    }

    public boolean isResponseTimeout() {
        return this == RESPONSE_TIMEOUT;
    }

    public boolean isConnectTimeout() {
        return this == CONNECT_TIMEOUT;
    }

    public boolean isConnectionPoolEmpty() {
        return this == CONNECTION_POOL_IS_EMPTY;
    }

    public boolean isRemoteServerUnreachable() {
        return this == SERVICE_UNREACHABLE;
    }
}
