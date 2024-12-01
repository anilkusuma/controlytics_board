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

import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.lang.reflect.Field;

@Slf4j
@SuperBuilder(toBuilder = true)
public class PdfGenerationContext {

    // Format startDateTime to 'dd/MM/yyyy HH:mm:ss'
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy & HH:mm:ss");

    public Context asContext() {
        PdfGenerationContext generatorContext = this;
        final Map<String, Object> contexVariablesAsMap = Arrays.stream(this.getClass().getDeclaredFields())
                .collect(Collectors.toMap(Field::getName, field -> {
                    try {
                        field.setAccessible(true);
                        if (StringUtils.hasText((CharSequence) field.get(generatorContext))) {
                            return field.get(generatorContext);
                        }
                        return "NA";
                    } catch (IllegalAccessException e) {
                        log.error("Error occurred while trying to read from PdfGeneratorContext object", e);
                        throw new RuntimeException("Error occurred while trying to read from " +
                                "PdfGeneratorContext object");
                    }
                }));
        final Context context = new Context();
        context.setVariables(contexVariablesAsMap);
        return context;
    }

    protected String getFormattedTime(final long time) {
        if (time == 0 || time == Long.MAX_VALUE) {
            return "";
        }
        final LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
        return dateTime.format(formatter);
    }

    protected String getFormattedTimeInIst(final long time) {
        if (time == 0 || time == Long.MAX_VALUE) {
            return "";
        }

        final ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        final LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), zoneId);
        return dateTime.format(formatter);
    }

    protected static <T> List<List<T>> paginateList(final List<T> auditLogs, int pageSize, int firstPageSize) {
        final List<List<T>> pages = new ArrayList<>();
        if (auditLogs.size() > firstPageSize) {
            pages.add(auditLogs.subList(0, firstPageSize));
            pages.addAll(paginateList(auditLogs.subList(firstPageSize, auditLogs.size()), pageSize));
        } else {
            pages.add(auditLogs);
        }
        return pages;
    }

    protected static <T> List<List<T>> paginateList(final List<T> auditLogs, int pageSize) {
        final List<List<T>> pages = new ArrayList<>();
        for (int i = 0; i < auditLogs.size(); i += pageSize) {
            pages.add(auditLogs.subList(i, Math.min(i + pageSize, auditLogs.size())));
        }
        return pages;
    }
}
