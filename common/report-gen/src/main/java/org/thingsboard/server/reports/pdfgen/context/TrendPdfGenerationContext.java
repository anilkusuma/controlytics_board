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
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thymeleaf.context.Context;

import java.util.Date;

@Data
@Slf4j
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class TrendPdfGenerationContext extends PdfGenerationContext {
    
    private final TenantId tenantId;
    private final EntityId entityId;
    private final String entityName;
    private final UserId userId;
    private final String userName;
    private final long startTs;
    private final long endTs;
    private final String chartImageBase64;
    private final String chartType; // "temperature" or "humidity" or "both"
    
    @Override
    public Context asContext() {
        final Context context = new Context();
        context.setVariable("tenantId", tenantId.getId().toString());
        context.setVariable("entityId", entityId.getId().toString());
        context.setVariable("entityName", entityName);
        context.setVariable("userId", userId.getId().toString());
        context.setVariable("username", userName);
        context.setVariable("startDate", getFormattedDateInIst(startTs));
        context.setVariable("startTime", getFormattedTimeInIst(startTs));
        context.setVariable("endDate", getFormattedDateInIst(endTs));
        context.setVariable("endTime", getFormattedTimeInIst(endTs));
        context.setVariable("printTime", getFormattedTimeInIst(new Date().getTime()));
        context.setVariable("chartImageBase64", chartImageBase64);
        context.setVariable("chartType", chartType);
        
        String chartTitle = "Temperature and Humidity Trend";
        if ("temperature".equals(chartType)) {
            chartTitle = "Temperature Trend";
        } else if ("humidity".equals(chartType)) {
            chartTitle = "Humidity Trend";
        }
        context.setVariable("chartTitle", chartTitle);
        
        return context;
    }
}