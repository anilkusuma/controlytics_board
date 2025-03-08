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
package org.thingsboard.server.reports.pdfgen.factory;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thingsboard.server.reports.pdfgen.IPdfGeneratorService;
import org.thingsboard.server.reports.pdfgen.models.PdfType;

@Component(value = "pdfGeneratorFactory")
public class PdfGeneratorFactory {

    private final IPdfGeneratorService timeseriesPdfReportGenerator;
    private final IPdfGeneratorService granulesTimeseriesPdfReportGenerator;
    private final IPdfGeneratorService auditLogReportGenerator;
    private final IPdfGeneratorService granulesAlarmReportGenerator;

    public PdfGeneratorFactory(@Qualifier(value = "timeseriesPdfReportGenerator")
                               final IPdfGeneratorService timeseriesPdfReportGenerator,
                               @Qualifier(value = "auditLogReportGenerator")
                               final IPdfGeneratorService auditLogReportGenerator,
                               @Qualifier(value = "granulesTimeseriesPdfReportGenerator")
                               final IPdfGeneratorService granulesTimeseriesPdfReportGenerator,
                               @Qualifier(value = "granulesAlarmPdfReportGenerator")
                               final IPdfGeneratorService granulesAlarmReportGenerator) {
        this.timeseriesPdfReportGenerator = timeseriesPdfReportGenerator;
        this.auditLogReportGenerator = auditLogReportGenerator;
        this.granulesTimeseriesPdfReportGenerator = granulesTimeseriesPdfReportGenerator;
        this.granulesAlarmReportGenerator = granulesAlarmReportGenerator;
    }

    public IPdfGeneratorService getGenerator(final PdfType pdfType) {
        return switch (pdfType) {
            case AUDIT_LOGS_REPORT -> auditLogReportGenerator;
            case TELEMETRY_REPORT -> timeseriesPdfReportGenerator;
            case GRANULES_DEVICE_TIMESERIES_REPORT -> granulesTimeseriesPdfReportGenerator;
            case GRANULES_ALARM_REPORT -> granulesAlarmReportGenerator;
            default -> throw new IllegalArgumentException("Unsupported pdf type: " + pdfType);
        };
    }
}
