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
package org.thingsboard.server.reports.pdfgen.generator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.server.reports.pdfgen.AbstractPdfGenerator;
import org.thymeleaf.TemplateEngine;

import static org.thingsboard.server.reports.pdfgen.config.PdfGeneratorSpringConfig.PDF_GENERATOR_TEMPLATE_BEAN;

@Component(value = "auditLogReportGenerator")
public class AuditLogReportGenerator extends AbstractPdfGenerator {

    @Autowired
    public AuditLogReportGenerator(@Value("${report_gen.storage_path}") String reportLocalStoragePath,
                                   @Qualifier(PDF_GENERATOR_TEMPLATE_BEAN) TemplateEngine templateEngine) {
        super("pdf_templates/audit-log-report", templateEngine, reportLocalStoragePath + "/audit-log-reports");
    }
}
