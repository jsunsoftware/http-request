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

//todo rename and make public
interface ConnectionFailureType {
    /**
     * @return true When have Any IO problem
     */
    boolean isFailed();

    /**
     * @return true When connection not failed
     */
    boolean isNotFailed();

    /**
     * @return true When remote server is high loaded. Response time expired or no http response.
     */
    boolean isRemoteServerHighLoaded();

    /**
     * @return true When time to connect remote server expired.
     */
    boolean isConnectTimeoutExpired();

    /**
     * @return true  When connection pool is empty.
     */
    boolean isConnectionPoolEmpty();

    /**
     * @return true When remote server is down
     */
    boolean isRemoteServerDown();
}
