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
import org.thymeleaf.context.Context;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class GranulesBasePdfGenerationContext extends PdfGenerationContext {
    protected static final String TEMPERATURE_NMT_ENABLED_ATTRIBUTE_KEY = "Enable_Temp_NMT";
    protected static final String TEMPERATURE_NLT_ENABLED_ATTRIBUTE_KEY = "Enable_Temp_NLT";
    protected static final String TEMPERATURE_NMT_VALUE_ATTRIBUTE_KEY = "High Temperature Limit";
    protected static final String TEMPERATURE_NLT_VALUE_ATTRIBUTE_KEY = "Low Temperature Limit";
    protected static final String HUMIDITY_NMT_ENABLED_ATTRIBUTE_KEY = "Enable_Humid_NMT";
    protected static final String HUMIDITY_NLT_ENABLED_ATTRIBUTE_KEY = "Enable_Humid_NLT";
    protected static final String HUMIDITY_NLT_VALUE_ATTRIBUTE_KEY = "Low Humidity Limit";
    protected static final String HUMIDITY_NMT_VALUE_ATTRIBUTE_KEY = "High Humidity Limit";
    protected static final String AREA_ATTRIBUTE_KEY = "Area";
    protected static final String LOCATION_ATTRIBUTE_KEY = "Location";
    protected static final String BUILDING_ATTRIBUTE_KEY = "Building";
    protected static final String PLANT_ATTRIBUTE_KEY = "Plant";

    protected final TenantId tenantId;
    protected final String reportId;
    protected final EntityId entityId;
    protected final String entityName;
    protected final UserId userId;
    protected final String userName;
    protected final long startTs;
    protected final long endTs;
    protected final List<AttributeKvEntry> sharedAttributes;

    @Override
    public Context asContext() {
        final Context context = new Context();
        context.setVariable("tenantId", tenantId.getId().toString());
        context.setVariable("entityId", entityId.getId().toString());
        context.setVariable("entityName", entityName);
        context.setVariable("userId", userId.getId().toString());
        context.setVariable("username", userName);
        context.setVariable("sharedAttributes", sharedAttributes);
        context.setVariable("startTime", getFormattedTimeInIst(startTs));
        context.setVariable("endTime", getFormattedTimeInIst(endTs));
        context.setVariable("printTime", getFormattedTimeInIst(new Date().getTime()));
        getSharedAttributes().forEach(attributeKvEntry -> {
            if (attributeKvEntry.getKey().equals(TEMPERATURE_NMT_ENABLED_ATTRIBUTE_KEY)) {
                context.setVariable("temperatureNmtEnabled", attributeKvEntry.getBooleanValue().orElse(false));
            } else if (attributeKvEntry.getKey().equals(TEMPERATURE_NLT_ENABLED_ATTRIBUTE_KEY)) {
                context.setVariable("temperatureNltEnabled", attributeKvEntry.getBooleanValue().orElse(false));
            } else if (attributeKvEntry.getKey().equals(TEMPERATURE_NMT_VALUE_ATTRIBUTE_KEY)) {
                context.setVariable("temperatureNmtValue", attributeKvEntry.getValue());
            } else if (attributeKvEntry.getKey().equals(TEMPERATURE_NLT_VALUE_ATTRIBUTE_KEY)) {
                context.setVariable("temperatureNltValue",
                        attributeKvEntry.getValue());
            } else if (attributeKvEntry.getKey().equals(HUMIDITY_NMT_ENABLED_ATTRIBUTE_KEY)) {
                context.setVariable("humidityNmtEnabled", attributeKvEntry.getBooleanValue().orElse(false));
            } else if (attributeKvEntry.getKey().equals(HUMIDITY_NLT_ENABLED_ATTRIBUTE_KEY)) {
                context.setVariable("humidityNltEnabled", attributeKvEntry.getBooleanValue().orElse(false));
            } else if (attributeKvEntry.getKey().equals(HUMIDITY_NLT_VALUE_ATTRIBUTE_KEY)) {
                context.setVariable("humidityNltValue", attributeKvEntry.getValue());
            } else if (attributeKvEntry.getKey().equals(HUMIDITY_NMT_VALUE_ATTRIBUTE_KEY)) {
                context.setVariable("humidityNmtValue", attributeKvEntry.getValue());
            } else if (attributeKvEntry.getKey().equals(AREA_ATTRIBUTE_KEY)) {
                context.setVariable("deviceArea", attributeKvEntry.getStrValue().orElse(""));
            } else if (attributeKvEntry.getKey().equals(LOCATION_ATTRIBUTE_KEY)) {
                context.setVariable("deviceLocation", attributeKvEntry.getStrValue().orElse(""));
            } else if (attributeKvEntry.getKey().equals(BUILDING_ATTRIBUTE_KEY)) {
                context.setVariable("deviceBuilding", attributeKvEntry.getStrValue().orElse(""));
            } else if (attributeKvEntry.getKey().equals(PLANT_ATTRIBUTE_KEY)) {
                context.setVariable("devicePlant", attributeKvEntry.getStrValue().orElse(""));
            }
        });
        return context;
    }
}
