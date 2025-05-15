/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.server.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;

public class ThingsboardAccountActivateRequiredResponse extends ThingsboardErrorResponse {

    private final String resetUrl;

    protected ThingsboardAccountActivateRequiredResponse(String message, String resetUrl) {
        super(message, ThingsboardErrorCode.CREATE_PASSWORD_VIOLATION, HttpStatus.UNAUTHORIZED);
        this.resetUrl = resetUrl;
    }

    public static ThingsboardPasswordResetRequiredResponse of(final String message, final String resetUrl) {
        return new ThingsboardPasswordResetRequiredResponse(message, resetUrl);
    }

    @Schema(description = "Password create url", accessMode = Schema.AccessMode.READ_ONLY)
    public String getResetToken() {
        return resetUrl;
    }
}
