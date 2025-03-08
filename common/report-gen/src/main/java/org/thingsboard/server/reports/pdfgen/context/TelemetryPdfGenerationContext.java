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
package org.thingsboard.server.reports.pdfgen.context;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thymeleaf.context.Context;

import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class TelemetryPdfGenerationContext  extends PdfGenerationContext {
    private final TenantId tenantId;
    private final String reportId;
    private final EntityId entityId;
    private final String entityName;
    private final UserId userId;
    private final String userName;
    private final long startTs;
    private final long endTs;
    private final long intervalInMs;
    private final long thresholdInMs;
    private final List<AttributeKvEntry> sharedAttributes;
    private final List<TsKvEntry> telemetryEntries;

    @Override
    public Context asContext() {
        final Context ctx = new Context();
        ctx.setVariable("tenantId", tenantId.getId().toString());
        ctx.setVariable("entityId", entityId.getId().toString());
        ctx.setVariable("entityName", entityName);
        ctx.setVariable("userId", userId.getId().toString());
        ctx.setVariable("username", userName);
        ctx.setVariable("startTs", startTs);
        ctx.setVariable("endTs", endTs);
        ctx.setVariable("intervalInMs", intervalInMs);
        ctx.setVariable("thresholdInMs", thresholdInMs);
        ctx.setVariable("sharedAttributes", sharedAttributes);
        ctx.setVariable("telemetryEntries", telemetryEntries);
        return ctx;
    }
}
