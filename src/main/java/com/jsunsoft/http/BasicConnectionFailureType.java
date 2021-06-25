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
    REMOTE_SERVER_HIGH_LOADED,
    CONNECT_TIMEOUT_EXPIRED,
    CONNECTION_POOL_IS_EMPTY,
    REMOTE_SERVER_IS_DOWN,
    IO,
    UNDEFINED;

    public boolean isFailed() {
        return this != NONE;
    }

    public boolean isNotFailed() {
        return this == NONE;
    }

    public boolean isRemoteServerHighLoaded() {
        return this == REMOTE_SERVER_HIGH_LOADED;
    }

    public boolean isConnectTimeoutExpired() {
        return this == CONNECT_TIMEOUT_EXPIRED;
    }

    public boolean isConnectionPoolEmpty() {
        return this == CONNECTION_POOL_IS_EMPTY;
    }

    public boolean isRemoteServerDown() {
        return this == REMOTE_SERVER_IS_DOWN;
    }
}
