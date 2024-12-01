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
import java.util.stream.Collectors;

@Data
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class GranulesAlarmPdfGenerationContext extends GranulesBasePdfGenerationContext {

    private final List<AlarmInfo> alarmDataList;

    @Override
    public Context asContext() {
        final Context context = super.asContext();
        final List<AlarmInfo> alarmList = getAlarmDataList();
        final List<PdfContextAlarmInfo> contextAlarm = alarmList.stream()
                .map(alarm -> {
                    final PdfContextAlarmInfo alarmInfo = new PdfContextAlarmInfo();
                    alarmInfo.setAlarmType(alarm.getType());
                    alarmInfo.setAlarmCreatedTime(getFormattedTimeInIst(alarm.getCreatedTime()));
                    alarmInfo.setAlarmSeverity(alarm.getSeverity().name());
                    alarmInfo.setAlarmClearTime(getFormattedTimeInIst(alarm.getClearTs()));
                    alarmInfo.setAlarmCreatedValue(alarm.getDetails().has("createdValue") ?
                            alarm.getDetails().get("createdValue").toString() : "");
                    alarmInfo.setAlarmClearedValue(alarm.getDetails().has("clearedValue") ?
                            alarm.getDetails().get("clearedValue").toString() : "");
                    return alarmInfo;
                }).sorted(Comparator.comparing(PdfContextAlarmInfo::getAlarmCreatedTime))
                .collect(Collectors.toList());

        context.setVariable("paginatedAlarmData", paginateList(contextAlarm, 17, 13));
        return context;
    }

    @Data
    @NoArgsConstructor
    public static class PdfContextAlarmInfo {
        private String alarmType;
        private String alarmCreatedTime;
        private String alarmCreatedValue;
        private String alarmSeverity;
        private String alarmClearTime;
        private String alarmClearedValue;
    }
}
