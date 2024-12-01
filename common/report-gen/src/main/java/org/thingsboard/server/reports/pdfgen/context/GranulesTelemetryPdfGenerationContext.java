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

import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thymeleaf.context.Context;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Slf4j
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class GranulesTelemetryPdfGenerationContext extends GranulesBasePdfGenerationContext {

    private final long intervalInMs;
    private final long thresholdInMs;
    private final List<TsKvEntry> telemetryEntries;

    @Override
    public Context asContext() {

        final Context context = super.asContext();
        context.setVariable("intervalInMs", intervalInMs);
        context.setVariable("thresholdInMs", thresholdInMs);
        context.setVariable("telemetryEntries", telemetryEntries);

        final List<TsKvEntry> telemetryEntries = getTelemetryEntries();
        final Map<Long, List<TsKvEntry>> telemetryEntriesByTs = telemetryEntries.stream()
                .collect(Collectors.groupingBy(TsKvEntry::getTs));
        final List<PdfContextTelemetry> contextTelemetry = telemetryEntriesByTs.entrySet().stream()
                .map(entry -> {
                    final PdfContextTelemetry telemetry = new PdfContextTelemetry();
                    telemetry.setDateTime(getFormattedTimeInIst(entry.getKey()));
                    entry.getValue().forEach(tsKvEntry -> {
                        if (tsKvEntry.getKey().equals("temperature")) {
                            telemetry.setTemperature(tsKvEntry.getValueAsString());
                        } else if (tsKvEntry.getKey().equals("humidity")) {
                            telemetry.setHumidity(tsKvEntry.getValueAsString());
                        }
                    });
                    telemetry.setRemarks("");
                    telemetry.setTs(String.valueOf(entry.getKey()));
                    return telemetry;
                }).sorted(Comparator.comparing(PdfContextTelemetry::getDateTime)).collect(Collectors.toList());
        final MinMaxHolder holder = new MinMaxHolder();
        log.info("minTemperature: {}, maxTemperature: {}, minHumidity: {}, maxHumidity: {}",
                holder.getMinTemperature(), holder.getMaxTemperature(), holder.getMinHumidity(), holder.getMaxHumidity());
        contextTelemetry.forEach(telemetry -> holder.accept(telemetry, this::parse));
        context.setVariable("minTemperature", holder.getMinTemperature());
        context.setVariable("maxTemperature", holder.getMaxTemperature());
        context.setVariable("minHumidity", holder.getMinHumidity());
        context.setVariable("maxHumidity", holder.getMaxHumidity());
        context.setVariable("paginatedTelemetryData", paginateList(contextTelemetry, 28, 18));
        return context;
    }

    @Data
    @NoArgsConstructor
    public static class PdfContextTelemetry {
        private String dateTime;
        private String temperature;
        private String humidity;
        private String ts;
        private String remarks;
    }


    private Double parse(final String value) {
        try {
            return Double.valueOf(value);
        } catch (final NumberFormatException e) {
            log.error("Error occurred while parsing value: {}, error - {}", value, e.getMessage());
            return null;
        }
    }

    @Getter
    public static class MinMaxHolder {
        private Double minTemperature = null;
        private Double maxTemperature = null;
        private Double minHumidity = null;
        private Double maxHumidity = null;

        public void accept(final PdfContextTelemetry telemetry, final Function<String, Double> parseFunction) {
            final Double temperature = parseFunction.apply(telemetry.getTemperature());
            final Double humidity = parseFunction.apply(telemetry.getHumidity());

            if (temperature != null) {
                minTemperature = minTemperature == null ? temperature : Math.min(minTemperature, temperature);
                maxTemperature = maxTemperature == null ? temperature : Math.max(maxTemperature, temperature);
            }

            if (humidity != null) {
                minHumidity = minHumidity == null ? humidity : Math.min(minHumidity, humidity);
                maxHumidity = maxHumidity == null ? humidity : Math.max(maxHumidity, humidity);
            }
        }
    }
}
