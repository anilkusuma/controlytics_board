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
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thymeleaf.context.Context;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class GranulesAlarmPdfGenerationContext extends GranulesBasePdfGenerationContext {

    private static final String HIGH_TEMPERATURE_ALARM = "High Temperature Alarm";
    private static final String HIGH_HUMIDITY_ALARM = "High Humidity Alarm";
    private static final String LOW_TEMPERATURE_ALARM = "Low Temperature Alarm";
    private static final String LOW_HUMIDITY_ALARM = "Low Humidity Alarm";

    private final List<AlarmInfo> alarmDataList;

    @Override
    public Context asContext() {
        final Context context = super.asContext();
        final List<AlarmInfo> alarmList = getAlarmDataList();
        final List<PdfContextAlarmInfo> contextAlarm = alarmList.stream()
                .map(alarm -> {
                    final PdfContextAlarmInfo alarmInfo = new PdfContextAlarmInfo();
                    alarmInfo.setAlarmType(getAlarmTypeDisplayValue(alarm.getType()));
                    if (alarm.getType().equals(HIGH_TEMPERATURE_ALARM) || alarm.getType().equals(LOW_TEMPERATURE_ALARM)) {
                        alarmInfo.setAlarmUom("°C");
                        if (context.getVariable("temperatureNmtEnabled") != null && context.getVariable(
                                "temperatureNmtEnabled") == Boolean.TRUE) {
                            alarmInfo.setHighSetPointValue(format(context, "temperatureNmtValue"));
                        } if (context.getVariable("temperatureNltEnabled") != null && context.getVariable(
                                "temperatureNltEnabled") == Boolean.TRUE) {
                            alarmInfo.setLowSetPointValue(format(context, "temperatureNltValue"));
                        }
                    } else if (alarm.getType().equals(HIGH_HUMIDITY_ALARM) || alarm.getType().equals(LOW_HUMIDITY_ALARM)) {
                        alarmInfo.setAlarmUom("%RH");
                        if (context.getVariable("humidityNmtEnabled") != null && context.getVariable(
                                "humidityNmtEnabled") == Boolean.TRUE) {
                            alarmInfo.setHighSetPointValue(format(context, "humidityNmtValue"));
                        } if (context.getVariable("humidityNltEnabled") != null && context.getVariable(
                                "humidityNltEnabled") == Boolean.TRUE) {
                            alarmInfo.setLowSetPointValue(format(context, "humidityNltValue"));
                        }
                    } else {
                        alarmInfo.setAlarmUom("N/A");
                    }

                    alarmInfo.setAlarmCreatedTimeEpoch(alarm.getCreatedTime());
                    alarmInfo.setAlarmCreatedDate(getFormattedDateInIst(alarm.getCreatedTime()));
                    alarmInfo.setAlarmCreatedTimeOnly(getFormattedTimeOnlyInIst(alarm.getCreatedTime()));
                    alarmInfo.setAlarmCreatedTime(getFormattedTimeInIst(alarm.getCreatedTime()));
                    alarmInfo.setAlarmSeverity(alarm.getSeverity().name());
                    alarmInfo.setAlarmClearTime(getFormattedTimeInIst(alarm.getClearTs()));
                    // Get decimal settings from context
                    Integer tempDecimals = (Integer) context.getVariable("temperatureDecimals");
                    Integer humidDecimals = (Integer) context.getVariable("humidityDecimals");
                    
                    // Determine which decimal setting to use based on alarm type
                    Integer decimals = null;
                    if (alarm.getType().equals(HIGH_TEMPERATURE_ALARM) || alarm.getType().equals(LOW_TEMPERATURE_ALARM)) {
                        decimals = tempDecimals;
                    } else if (alarm.getType().equals(HIGH_HUMIDITY_ALARM) || alarm.getType().equals(LOW_HUMIDITY_ALARM)) {
                        decimals = humidDecimals;
                    }
                    
                    Double createdValue = alarm.getDetails().has("createdValue") ?
                            extractDecimal(alarm.getDetails().get("createdValue").toString()) : null;
                    Double clearedValue = alarm.getDetails().has("clearedValue") ?
                            extractDecimal(alarm.getDetails().get("clearedValue").toString()) : null;
                    
                    alarmInfo.setAlarmCreatedValue(formatAlarmValue(createdValue, decimals, alarm.getType()));
                    alarmInfo.setAlarmClearedValue(formatAlarmValue(clearedValue, decimals, alarm.getType()));
                    return alarmInfo;
                }).sorted(Comparator.comparing(PdfContextAlarmInfo::getAlarmCreatedTimeEpoch)
                        .reversed())
                .collect(Collectors.toList());

        context.setVariable("paginatedAlarmData", paginateList(contextAlarm, 16, 11));
        return context;
    }

    private static Double extractDecimal(final String input) {
        final String regex = "\\d+\\.?\\d*";
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        final java.util.regex.Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            return Double.parseDouble(matcher.group());
        }

        return null;
    }
    
    private String formatAlarmValue(Double value, Integer decimals, String alarmType) {
        if (value == null) {
            return "";
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

    private static String format(final Context context, final String key) {
        if (context.getVariable(key) != null) {
            if (context.getVariable(key) instanceof Long) {
                return String.format("%.1f", ((Long) context.getVariable(key)).doubleValue());
            }
            if (context.getVariable(key) instanceof Double) {
                return String.format("%.1f", (Double) context.getVariable(key));
            }
            return context.getVariable(key).toString();
        } else {
            return "N/A";
        }
    }

    private String getAlarmTypeDisplayValue(final String alarmType) {
        if (alarmType.equals(HIGH_TEMPERATURE_ALARM)) {
            return "High Temperature";
        } else if (alarmType.equals(HIGH_HUMIDITY_ALARM)) {
            return "RH High";
        } else if (alarmType.equals(LOW_TEMPERATURE_ALARM)) {
            return "Low Temperature";
        } else if (alarmType.equals(LOW_HUMIDITY_ALARM)) {
            return "RH Low";
        }
        return alarmType;
    }

    @Data
    @NoArgsConstructor
    public static class PdfContextAlarmInfo {
        private String alarmType;
        private long alarmCreatedTimeEpoch;
        private String alarmCreatedDate;
        private String alarmCreatedTime;
        private String alarmCreatedTimeOnly;
        private String alarmCreatedValue;
        private String alarmUom;
        private String alarmSeverity;
        private String alarmClearTime;
        private String alarmClearedValue;
        private String highSetPointValue;
        private String lowSetPointValue;
    }
}
