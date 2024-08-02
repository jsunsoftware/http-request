package com.jsunsoft.http;

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


import com.jsunsoft.http.annotations.Beta;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.LoggerFactory;

@Beta
public interface RetryContext {
    int getRetryCount();

    /**
     * @param response last executed response instance
     *
     * @return time in sec to wait before retry
     */
    default int getRetryDelay(Response response) {
        int retryAfterSec = 5;
        Header retryAfter = response.getFirstHeader(HttpHeaders.RETRY_AFTER);

        if (retryAfter != null) {
            try {
                retryAfterSec = Integer.parseInt(retryAfter.getValue());
            } catch (NumberFormatException e) {
                LoggerFactory.getLogger(RetryContext.class)
                        .warn("Can't parse {} header's value: {} to integer. {}", HttpHeaders.RETRY_AFTER, retryAfter.getValue(), e.getMessage());
            }
        }

        return retryAfterSec;
    }

    /**
     * @param response last executed response instance
     *
     * @return if true the request will be retried else no action
     */
    default boolean mustBeRetried(Response response) {
        return response.getCode() == HttpStatus.SC_SERVICE_UNAVAILABLE;
    }

    /**
     * The method will be called before retry
     *
     * @param webTarget current web target instance
     *
     * @return the webTarget instance which is responsible to do retry
     */
    default WebTarget beforeRetry(WebTarget webTarget) {
        return webTarget;
    }
}
