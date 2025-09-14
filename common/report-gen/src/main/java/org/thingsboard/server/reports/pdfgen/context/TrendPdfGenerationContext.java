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
import org.thymeleaf.context.Context;

@Data
@Slf4j
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class TrendPdfGenerationContext extends PdfGenerationContext {
    
    private final String entityId;
    private final String entityType;
    private final String entityName;
    private final String chartTitle;
    private final String chartImageBase64;
    private final String startDate;
    private final String startTime;
    private final String endDate;
    private final String endTime;
    private final String username;
    
    @Override
    public Context asContext() {
        final Context context = new Context();
        context.setVariable("entityId", entityId);
        context.setVariable("entityType", entityType);
        context.setVariable("entityName", entityName);
        context.setVariable("chartTitle", chartTitle != null ? chartTitle : "Temperature and Humidity Trend");
        context.setVariable("chartImageBase64", chartImageBase64);
        context.setVariable("startDate", startDate);
        context.setVariable("startTime", startTime);
        context.setVariable("endDate", endDate);
        context.setVariable("endTime", endTime);
        context.setVariable("username", username);
        context.setVariable("printTime", getFormattedTimeInIst(System.currentTimeMillis()));
        
        return context;
    }
}