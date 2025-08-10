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
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Data
@Slf4j
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class GranulesTelemetryPdfGenerationContext extends GranulesBasePdfGenerationContext {

    // Default values for MKT calculation
    private static final double DEFAULT_DELTA_H = 83144.0; // Activation energy in J/mol
    private static final double DEFAULT_GAS_CONSTANT_R = 8.3144; // Universal gas constant in J/(mol·K)

    private final long intervalInMs;
    private final long thresholdInMs;
    private final List<TsKvEntry> telemetryEntries;
    private final Double deltaH;
    private final Double gasConstantR;
    private final Boolean isMktCalculationEnabled;

    @Override
    public Context asContext() {

        final Context context = super.asContext();
        context.setVariable("intervalInMs", intervalInMs);
        context.setVariable("readableInterval", convertMsToReadableFormat(intervalInMs));
        context.setVariable("thresholdInMs", thresholdInMs);
        context.setVariable("telemetryEntries", telemetryEntries);

        final List<TsKvEntry> telemetryEntries = getTelemetryEntries();
        final Map<Long, List<TsKvEntry>> telemetryEntriesByTs = telemetryEntries.stream()
                .collect(Collectors.groupingBy(TsKvEntry::getTs));
        final Map<String, List<TsKvEntry>> result = telemetryEntriesByTs.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> getFormattedTimeInIst(e.getKey()), // assuming you're using org.joda.time.DateTime
                        Map.Entry::getValue
                ));
        final List<PdfContextTelemetry> contextTelemetry = telemetryEntriesByTs.entrySet().stream()
                .map(entry -> {
                    final PdfContextTelemetry telemetry = new PdfContextTelemetry();

                    telemetry.setDateTimeEpoch(entry.getKey());
                    telemetry.setDateTime(getFormattedTimeInIst(entry.getKey()));
                    telemetry.setDate(getFormattedDateInIst(entry.getKey()));
                    telemetry.setTime(getFormattedTimeOnlyInIst(entry.getKey()));
                    entry.getValue().forEach(tsKvEntry -> {
                        if (tsKvEntry.getKey().equals("temperature")) {
                            String value = tsKvEntry.getValueAsString();
                            if (value != null && !value.isEmpty() && value.matches("-?\\d+(\\.\\d+)?")) {
                                final double doubleValue = Double.parseDouble(value);
                                if ((Objects.nonNull(super.temperatureNmtValue) && doubleValue > super.temperatureNmtValue)
                                        || (Objects.nonNull(super.temperatureNltValue) && doubleValue < super.temperatureNltValue)) {
                                    telemetry.setTempBolded(true);
                                }
                                Integer tempDecimals = (Integer) context.getVariable("temperatureDecimals");
                                telemetry.setTemperature(formatValueWithPrecision(Double.parseDouble(value), tempDecimals));
                            } else if (value != null) {
                                telemetry.setTemperature(value);
                            } else {
                                telemetry.setTemperature("NA");
                            }
                            telemetry.setUom("°C");
                        } else if (tsKvEntry.getKey().equals("humidity")) {
                            // Check if humidity is disabled for this device
                            Boolean isHumidityDisabled = (Boolean) context.getVariable("isHumidityDisabled");
                            if (Boolean.TRUE.equals(isHumidityDisabled)) {
                                telemetry.setHumidity("");
                            } else {
                                String value = tsKvEntry.getValueAsString();
                                if (value != null && !value.isEmpty() && value.matches("-?\\d+(\\.\\d+)?")) {
                                    final double doubleValue = Double.parseDouble(value);
                                    if ((Objects.nonNull(super.humidityNmtValue) && doubleValue > super.humidityNmtValue)
                                            || (Objects.nonNull(super.humidityNltValue) && doubleValue < super.humidityNltValue)) {
                                        telemetry.setHumBolded(true);
                                    }
                                    Integer humidDecimals = (Integer) context.getVariable("humidityDecimals");
                                    telemetry.setHumidity(formatValueWithPrecision(Double.parseDouble(value), humidDecimals));
                                } else if (value != null) {
                                    telemetry.setHumidity(value);
                                } else {
                                    telemetry.setHumidity("N/A");
                                }
                                telemetry.setUom("%RH");
                            }
                        }
                    });
                    telemetry.setTs(String.valueOf(entry.getKey()));
                    return telemetry;
                }).sorted(Comparator.comparing(PdfContextTelemetry::getDateTimeEpoch)).collect(Collectors.toList());
        final MinMaxHolder holder = new MinMaxHolder();
        log.info("minTemperature: {}, maxTemperature: {}, minHumidity: {}, maxHumidity: {}",
                holder.getMinTemperature(), holder.getMaxTemperature(), holder.getMinHumidity(), holder.getMaxHumidity());
        contextTelemetry.forEach(telemetry -> holder.accept(telemetry, this::parse));
        context.setVariable("minTemperature", holder.getMinTemperature());
        context.setVariable("maxTemperature", holder.getMaxTemperature());
        context.setVariable("minHumidity", holder.getMinHumidity());
        context.setVariable("maxHumidity", holder.getMaxHumidity());
        
        // Get MKT parameters from shared attributes (set by parent context)
        Boolean mktEnabled = (Boolean) context.getVariable("isMktCalculationEnabled");
        Double deltaHFromAttr = (Double) context.getVariable("deltaH");
        Double gasConstantRFromAttr = (Double) context.getVariable("gasConstantR");
        
        // Calculate MKT if enabled
        if (Boolean.TRUE.equals(mktEnabled)) {
            // Use provided values or defaults
            double deltaHValue = deltaHFromAttr != null ? deltaHFromAttr : DEFAULT_DELTA_H;
            double gasConstantRValue = gasConstantRFromAttr != null ? gasConstantRFromAttr : DEFAULT_GAS_CONSTANT_R;
            
            Double mktValue = calculateMKT(contextTelemetry, deltaHValue, gasConstantRValue);
            context.setVariable("mktValue", mktValue != null ? String.format("%.1f", mktValue) : "N/A");
            context.setVariable("isMktCalculationEnabled", true);
        } else {
            context.setVariable("isMktCalculationEnabled", false);
        }
        
        context.setVariable("paginatedTelemetryData", paginateList(contextTelemetry, 28, 18));
        return context;
    }

    @Data
    @NoArgsConstructor
    public static class PdfContextTelemetry {
        private long dateTimeEpoch;
        private String dateTime;
        private String date;
        private String time;
        private String temperature;
        private String humidity;
        private String uom;
        private String ts;
        private String remarks;
        private boolean isTempBolded;
        private boolean isHumBolded;
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

    private String formatDecimalValue(double value) {
        // Preserve original decimal precision
        String stringValue = String.valueOf(value);
        
        // Handle scientific notation
        if (stringValue.contains("E")) {
            return String.format("%.1f", value);
        }
        
        // For whole numbers, ensure .0 is preserved
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.format("%.1f", value);
        }
        
        // For decimal numbers, preserve their precision
        return stringValue;
    }
    
    private String formatValueWithPrecision(Double value, Integer decimals) {
        if (value == null) {
            return "NA";
        }
        
        // If decimals is not set, mirror the input data
        if (decimals == null) {
            String stringValue = String.valueOf(value);
            
            // Handle scientific notation
            if (stringValue.contains("E")) {
                return stringValue;
            }
            
            // For whole numbers, don't add .0
            if (value == Math.floor(value) && !Double.isInfinite(value)) {
                return String.valueOf(value.intValue());
            }
            
            // For decimal numbers, preserve their precision
            return stringValue;
        }
        
        // If decimals is set, format with specified precision
        String format = "%%.%df".formatted(decimals);
        return String.format(format, value);
    }

    private Double calculateMKT(List<PdfContextTelemetry> telemetryData, double deltaH, double gasConstantR) {
        List<Double> validTemperatures = new ArrayList<>();
        
        for (PdfContextTelemetry entry : telemetryData) {
            String tempStr = entry.getTemperature();
            Double temp = parse(tempStr);
            
            // Only include valid numeric temperature values, skip NA/OL/Error entries entirely
            if (temp != null && !tempStr.equals("NA") && !tempStr.equals("OL") && !tempStr.equals("Error")) {
                validTemperatures.add(temp + 273.15); // Convert to Kelvin
            }
            // Skip invalid entries - don't use previous valid value
        }
        
        if (validTemperatures.isEmpty()) {
            return null;
        }
        
        // MKT = -ΔH/R × ln(Σ(exp(-ΔH/(R×Ti)))/n)
        double sum = 0.0;
        for (Double tempKelvin : validTemperatures) {
            sum += Math.exp(-deltaH / (gasConstantR * tempKelvin));
        }
        
        double mktKelvin = -deltaH / (gasConstantR * Math.log(sum / validTemperatures.size()));
        return mktKelvin - 273.15; // Convert back to Celsius
    }

    private String convertMsToReadableFormat(long milliseconds) {
        // Convert milliseconds into different time units
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        // Calculate remaining hours and minutes from the total
        hours = hours % 24;
        minutes = minutes % 60;
        seconds = seconds % 60;

        // Build the readable format string dynamically
        StringBuilder readableFormat = new StringBuilder();

        if (days > 0) {
            readableFormat.append(days).append(" day").append(days > 1 ? "s" : "").append(", ");
        }
        if (hours > 0) {
            readableFormat.append(hours).append(" hour").append(hours > 1 ? "s" : "").append(", ");
        }
        if (minutes > 0) {
            readableFormat.append(minutes).append(" minute").append(minutes > 1 ? "s" : "").append(", ");
        }
        if (seconds > 0 || readableFormat.length() == 0) { // include seconds only if non-zero or everything else is zero
            readableFormat.append(seconds).append(" second").append(seconds > 1 ? "s" : "");
        }

        // Remove trailing comma and space, if any
        if (readableFormat.toString().endsWith(", ")) {
            readableFormat.setLength(readableFormat.length() - 2);
        }

        return readableFormat.toString();
    }
}
