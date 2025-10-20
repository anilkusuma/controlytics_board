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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.audit.AuditLog;
import org.thingsboard.server.common.data.id.TenantId;
import org.thymeleaf.context.Context;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuperBuilder(toBuilder = true)
public class AuditLogPdfGenerationContext extends PdfGenerationContext {

    private TenantId tenantId;
    private long startTime;
    private long endTime;
    private String userName;
    private List<AuditLog> auditLogs;

    @Override
    public Context asContext() {
        final Context context = new Context();
        context.setVariable("tenantId", tenantId.getId().toString());
        context.setVariable("userName", userName);
        context.setVariable("startDate", getFormattedDateInIst(startTime));
        context.setVariable("startTime", getFormattedTimeOnlyInIst(startTime));
        context.setVariable("endDate", getFormattedDateInIst(endTime));
        context.setVariable("endTime", getFormattedTimeOnlyInIst(endTime));
        context.setVariable("printTime", getFormattedTimeInIst(new Date().getTime()));
        final List<PdfContextAuditLog> contextAuditLogs =
                auditLogs.stream()
                        .filter(auditLog -> !ActionType.ADDED_COMMENT.equals(auditLog.getActionType()))
                        .map(auditLog -> PdfContextAuditLog.builder().tenantId(auditLog.getTenantId().getId().toString())
                        .createdTimeLong(auditLog.getCreatedTime())
                        .createdTime(getFormattedTimeInIst(auditLog.getCreatedTime()))
                        .createdTimeOnly(getFormattedTimeOnlyInIst(auditLog.getCreatedTime()))
                        .createdDate(getFormattedDateInIst(auditLog.getCreatedTime()))
                        .entityId(auditLog.getEntityId().getId().toString())
                        .entityType(auditLog.getEntityId().getEntityType().name())
                        .entityName(auditLog.getEntityName())
                        .userId(auditLog.getUserId().getId().toString())
                        .userName(auditLog.getUserName())
                        .actionType(getActionTypeString(auditLog))
                        .status(auditLog.getActionStatus().name())
                        .details(getAuditLogDetails(auditLog))
                        .build()
                ).sorted(Comparator.comparing(PdfContextAuditLog::getCreatedTimeLong))
                        .collect(Collectors.toList());
        final List<List<PdfContextAuditLog>> paginatedAuditLogs = paginateList(contextAuditLogs, 15, 10);
        context.setVariable("paginatedAuditLogs", paginatedAuditLogs);
        context.setVariable("auditLogs", contextAuditLogs);
        return context;
    }

    private String getAuditLogDetails(final AuditLog auditLog) {
        if (auditLog.getActionType() == ActionType.ALARM_ACK
                || auditLog.getActionType() == ActionType.ALARM_ASSIGNED
                || auditLog.getActionType() == ActionType.ALARM_CLEAR
                || auditLog.getActionType() == ActionType.ALARM_DELETE) {
            return "ALARM: " + auditLog.getEntityName() + "\n" + getAuditLogSubDetails(auditLog);
        }
        return getAuditLogSubDetails(auditLog);
    }

    private String getAuditLogSubDetails(final AuditLog auditLog) {
        final JsonNode actionData = auditLog.getActionData();
        switch (auditLog.getActionType()) {
            case ATTRIBUTES_DELETED:
            case ATTRIBUTES_READ:
                ArrayNode attributes = (ArrayNode) actionData.get("attributes");
                return attributes != null ? String.join(", ", attributes.toString()) : "";
            case ATTRIBUTES_UPDATED:
                final StringBuilder result = new StringBuilder();
                final JsonNode attributesData = actionData.get("attributes");
                if (Objects.nonNull(attributesData)) {
                    attributesData.fields().forEachRemaining(entry -> {
                        final JsonNode attributeValue = entry.getValue();
                        if (attributeValue.isObject() && attributeValue.has("old_value")) {
                            result.append(entry.getKey())
                                    .append(": Changed FROM: ")
                                    .append(attributeValue.get("old_value").asText())
                                    .append(" TO: ")
                                    .append(attributeValue.get("new_value").asText())
                                    .append("\n");
                        } else if (attributeValue.isObject()) {
                            result.append(entry.getKey())
                                    .append(": Added: ")
                                    .append(attributeValue.get("new_value").asText())
                                    .append("\n");
                        } else if (attributeValue.isArray()) {
                            result.append(entry.getKey())
                                    .append(": ")
                                    .append(String.join(", ", attributeValue.toString()))
                                    .append("\n");
                        } else if (!attributeValue.isNull()) {
                            result.append(entry.getKey())
                                    .append(": Changed TO: ")
                                    .append(attributeValue.asText())
                                    .append("\n");
                        }
                    });
                    if (actionData.has("remarks")) {
                        result.append("Remarks: ").append(actionData.get("remarks").asText());
                    }
                }
                return result.toString();
            case LOGIN:
                return "Login";
            case LOGOUT:
                return "Logout";
            case LOCKOUT:
                return "Lockout";
            case CREDENTIALS_RESET_REQUEST:
                return "Forgot Password Request";
            case ALARM_ASSIGNED:
            case ALARM_ACK:
            case ALARM_CLEAR:
            case ALARM_DELETE:
                return "Device : " + getDeviceName(auditLog) + "\n"
                        + (actionData.has("entity")
                        && actionData.get("entity").has("details")
                        && actionData.get("entity").get("details").has("clearedValue") ?
                        "Cleared Value: " + actionData.get("entity").get("details").get("clearedValue").asText() + "\n" : "")
                        + (actionData.has("entity")
                        && actionData.get("entity").has("details")
                        && actionData.get("entity").get("details").has("createdValue")
                        ? "Created Value: " + actionData.get("entity").get("details").get("createdValue").asText() + "\n" : "");
            case REPORT_GENERATED:
                StringBuilder reportDetails = new StringBuilder();
                if (actionData.has("reportName")) {
                    reportDetails.append("Report: ").append(actionData.get("reportName").asText()).append("\n");
                }
                if (actionData.has("startTimeInMs") && actionData.has("endTimeInMs")) {
                    long startTime = actionData.get("startTimeInMs").asLong();
                    long endTime = actionData.get("endTimeInMs").asLong();
                    if (startTime > 0) {
                        reportDetails.append("Start Time: ").append(getFormattedTimeInIst(startTime)).append("\n");
                    }
                    if (endTime > 0) {
                        reportDetails.append("End Time: ").append(getFormattedTimeInIst(endTime)).append("\n");
                    }
                }
                return reportDetails.toString();
            case UPDATED:
                // Check if this is a password policy update
                if (actionData.has("settingsType") && "PASSWORD_POLICY".equals(actionData.get("settingsType").asText())) {
                    return "Password Policy Updated\n";
                }
                // Fall through to default for other updates
            default:
                return getActionTypeString(auditLog) + "\n"
                        + auditLog.getEntityId().getEntityType().name() + ":" + auditLog.getEntityName() + "\n";
        }
    }

    private String getDeviceName(final AuditLog auditLog) {
        return switch (auditLog.getActionType()) {
            case ALARM_ASSIGNED, ALARM_ACK, ALARM_CLEAR, ALARM_DELETE ->
                    auditLog.getActionData().get("entity").get("originatorName").asText();
            default -> "";
        };
    }

    private String getActionTypeString(final AuditLog auditLog) {
        switch (auditLog.getActionType()) {
            case ATTRIBUTES_UPDATED:
                return "Settings Updated";
            case ATTRIBUTES_DELETED:
                return "Settings Deleted";
            case ATTRIBUTES_READ:
                return "Settings Read";
            case ALARM_ACK:
                return "Alarm Acknowledged";
            case ALARM_CLEAR:
                return "Alarm Cleared";
            case ALARM_DELETE:
                return "Alarm Deleted";
            case ALARM_ASSIGNED:
                return "Alarm Assigned";
            case LOGIN:
                return "Login";
            case LOGOUT:
                return "Logout";
            case LOCKOUT:
                return "Lockout";
            case REPORT_GENERATED:
                // Check if this is a trend report for more specific naming
                if (auditLog.getActionData() != null && auditLog.getActionData().has("reportId")) {
                    String reportId = auditLog.getActionData().get("reportId").asText();
                    if ("TREND_REPORT".equals(reportId)) {
                        return "Trend Report Generated";
                    }
                }
                return "Report Generated";
            case UPDATED:
                // Check if this is a security settings update using the simple identifier
                if (auditLog.getActionData() != null && auditLog.getActionData().has("settingsType")) {
                    String settingsType = auditLog.getActionData().get("settingsType").asText();
                    if ("PASSWORD_POLICY".equals(settingsType)) {
                        return "Password Policy Updated";
                    } else if ("JWT_SETTINGS".equals(settingsType)) {
                        return "Auto Logout Settings Updated";
                    }
                }
                return "Updated";
            default:
                return auditLog.getActionType().name().replace("_", " ");
        }
    }

    @Data
    @Builder
    public static final class PdfContextAuditLog {
        private String tenantId;
        private long createdTimeLong;
        private String createdTime;
        private String createdDate;
        private String createdTimeOnly;
        private String entityId;
        private String entityType;
        private String entityName;
        private String deviceName;
        private String userId;
        private String userName;
        private String status;
        private String actionType;
        private String details;
    }
}
