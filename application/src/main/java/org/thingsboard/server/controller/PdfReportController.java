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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonParseException;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmQueryV2;
import org.thingsboard.server.common.data.alarm.AlarmSearchStatus;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.kv.*;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.reports.ReportGenerationAuditLogData;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.audit.AuditLogService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.reports.pdfgen.context.GranulesAlarmPdfGenerationContext;
import org.thingsboard.server.reports.pdfgen.context.GranulesTelemetryPdfGenerationContext;
import org.thingsboard.server.reports.pdfgen.context.TelemetryPdfGenerationContext;
import org.thingsboard.server.reports.pdfgen.factory.PdfGeneratorFactory;
import org.thingsboard.server.reports.pdfgen.models.PdfType;
import org.thingsboard.server.service.security.AccessValidator;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.permission.Operation;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.thingsboard.server.controller.ControllerConstants.*;

@RestController
@TbCoreComponent
@RequestMapping(TbUrlConstants.REPORTS_URL_PREFIX)
@Slf4j
public class PdfReportController extends BaseController {

    private static final String ALARM_QUERY_SEARCH_STATUS_ARRAY_DESCRIPTION = "A list of string values separated by comma ',' representing one of the AlarmSearchStatus enumeration value";
    private static final String ALARM_QUERY_SEVERITY_ARRAY_DESCRIPTION = "A list of string values separated by comma ',' representing one of the AlarmSeverity enumeration value";

    private static final String ALARM_QUERY_TYPE_ARRAY_DESCRIPTION = "A list of string values separated by comma ',' representing alarm types";
    private static final String ALARM_QUERY_ASSIGNEE_DESCRIPTION = "A string value representing the assignee user id. For example, '784f394c-42b6-435a-983c-b7beff2784f9'";
    private static final String ALARM_QUERY_START_TIME_DESCRIPTION = "The start timestamp in milliseconds of the search time range over the Alarm class field: 'createdTime'.";
    private static final String ALARM_QUERY_END_TIME_DESCRIPTION = "The end timestamp in milliseconds of the search time range over the Alarm class field: 'createdTime'.";


    @Autowired
    private TimeseriesService tsService;

    @Autowired
    private PdfGeneratorFactory pdfGeneratorFactory;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AccessValidator accessValidator;

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/{entityType}/{entityId}/values/timeseries/pdf", method = RequestMethod.GET, params = {
            "keys", "startTs", "endTs"})
    @ResponseBody
    public DeferredResult<ResponseEntity> getTimeseriesReportAsPdf(
            @Parameter(description = ENTITY_TYPE_PARAM_DESCRIPTION, required = true, schema = @Schema(defaultValue = "DEVICE")) @PathVariable("entityType") String entityType,
            @Parameter(description = ENTITY_ID_PARAM_DESCRIPTION, required = true) @PathVariable("entityId") String entityIdStr,
            @Parameter(description = TELEMETRY_KEYS_BASE_DESCRIPTION, required = true) @RequestParam(name = "keys") String keys,
            @Parameter(description = "A long value representing the start timestamp of the time range in milliseconds, UTC.")
            @RequestParam(name = "startTs") Long startTs,
            @Parameter(description = "A long value representing the end timestamp of the time range in milliseconds, UTC.")
            @RequestParam(name = "endTs") Long endTs,
            @Parameter(description = "A string value representing the type fo the interval.", schema = @Schema(allowableValues = {"MILLISECONDS", "WEEK", "WEEK_ISO", "MONTH", "QUARTER"}))
            @RequestParam(name = "intervalType", required = false) IntervalType intervalType,
            @Parameter(description = "A long value representing the aggregation interval range in milliseconds.")
            @RequestParam(name = "interval", defaultValue = "0") Long interval,
            @RequestParam(name = "agg", defaultValue = "NONE") String aggStr,
            @RequestParam(name = "threshold", defaultValue = "0") Long threshold,
            @Parameter(description = "A string value representing the timezone that will be used to calculate exact timestamps for 'WEEK', 'WEEK_ISO', 'MONTH' and 'QUARTER' interval types.")
            @RequestParam(name = "timeZone", required = false) String timeZone,
            @Parameter(description = SORT_ORDER_DESCRIPTION, schema = @Schema(allowableValues = {"ASC", "DESC"}))
            @RequestParam(name = "orderBy", defaultValue = "DESC") String orderBy,
            @RequestParam(name = "limit", defaultValue = "10000") Integer limit,
            @Parameter(schema = @Schema(allowableValues = {"GRANULES_DEVICE_REPORT"}))
            @RequestParam(name = "reportId") String reportId,
            @Parameter(description = STRICT_DATA_TYPES_DESCRIPTION)
            @RequestParam(name = "useStrictDataTypes", required = false, defaultValue = "false") Boolean useStrictDataTypes) throws ThingsboardException {
        final SecurityUser currentUser = getCurrentUser();
        return accessValidator.validateEntityAndCallback(currentUser, Operation.READ_TELEMETRY, entityType, entityIdStr,
                (result, tenantId, entityId) -> {
                    AggregationParams params;
                    Aggregation agg = Aggregation.valueOf(aggStr);
                    if (Aggregation.NONE.equals(agg)) {
                        params = AggregationParams.none();
                    } else if (Aggregation.CLOSEST.equals(agg)) {
                        params = interval == 0L ? AggregationParams.none() : AggregationParams.closest(interval,
                                threshold);
                    } else if (intervalType == null || IntervalType.MILLISECONDS.equals(intervalType)) {
                        params = interval == 0L ? AggregationParams.none() : AggregationParams.milliseconds(agg, interval);
                    } else {
                        params = AggregationParams.none();
                    }
                    Futures.addCallback(attributesService.findAll(tenantId, entityId, AttributeScope.SHARED_SCOPE), new FutureCallback<>() {
                        @Override
                        public void onSuccess(List<AttributeKvEntry> attributeKvEntries) {
                            try {
                                if (entityId.getEntityType() != EntityType.DEVICE) {
                                    throw new IllegalArgumentException("Only device entities are supported!");
                                }
                                final Device device = deviceService.findDeviceById(tenantId,
                                        (DeviceId) entityId);
                                final List<ReadTsKvQuery> queries = toKeysList(keys).stream()
                                        .map(key -> new BaseReadTsKvQuery(key, startTs, endTs, params, limit, orderBy))
                                        .collect(Collectors.toList());
                                Futures.addCallback(tsService.findAll(tenantId, entityId, queries),
                                        getTsPdfCallback(result, ReportId.GRANULES_DEVICE_TIMESERIES_REPORT,
                                                currentUser,
                                                device, queries, attributeKvEntries),
                                        MoreExecutors.directExecutor());
                            } catch (final Exception e) {
                                log.error("Failed to generate pdf report", e);
                                AccessValidator.handleError(e, result, HttpStatus.INTERNAL_SERVER_ERROR);
                            }
                        }

                        @Override
                        public void onFailure(final Throwable e) {
                            log.error("Failed to fetch historical data", e);
                            AccessValidator.handleError(e, result, HttpStatus.INTERNAL_SERVER_ERROR);
                        }
                    }, MoreExecutors.directExecutor());
                });
    }

    private List<String> toKeysList(String keys) {
        List<String> keyList = null;
        if (!StringUtils.isEmpty(keys)) {
            keyList = Arrays.asList(keys.split(","));
        }
        return keyList;
    }

    private FutureCallback<List<TsKvEntry>> getTsPdfCallback(final DeferredResult<ResponseEntity> response,
                                                             final ReportId reportId,
                                                             final SecurityUser currentUser,
                                                             final Device device,
                                                             final List<ReadTsKvQuery> queries,
                                                             final List<AttributeKvEntry> attributeKvEntries) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(List<TsKvEntry> data) {
                try {
                    final byte[] result = pdfGeneratorFactory.getGenerator(PdfType.valueOf(reportId.name()))
                            .generatePdf(UUID.randomUUID().toString(),
                                    GranulesTelemetryPdfGenerationContext.builder()
                                            .tenantId(currentUser.getTenantId())
                                            .reportId(reportId.name())
                                            .entityId(device.getId())
                                            .entityName(device.getName())
                                            .userId(currentUser.getId())
                                            .userName(currentUser.getName())
                                            .startTs(queries.get(0).getStartTs())
                                            .endTs(queries.get(0).getEndTs())
                                            .intervalInMs(queries.get(0).getInterval())
                                            .thresholdInMs(queries.get(0).getAggParameters().getThresholdInMs())
                                            .sharedAttributes(attributeKvEntries)
                                            .telemetryEntries(data)
                                            .build());
                    response.setResult(new ResponseEntity<>(result, HttpStatus.OK));
                    publishAuditLog(currentUser, device, reportId, queries.get(0).getStartTs(),
                            queries.get(0).getEndTs(), null);
                } catch (final Exception e) {
                    log.error("Failed to generate pdf report", e);
                    publishAuditLog(currentUser, device, reportId, queries.get(0).getStartTs(),
                            queries.get(0).getEndTs(), e);
                    AccessValidator.handleError(e, response, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }

            @Override
            public void onFailure(final Throwable e) {
                log.error("Failed to fetch historical data", e);
                publishAuditLog(currentUser, device, reportId, queries.get(0).getStartTs(), queries.get(0).getEndTs()
                        , new RuntimeException(e));
                AccessValidator.handleError(e, response, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
    }

    private void publishAuditLog(final SecurityUser currentUser,
                                 final Device device,
                                 final ReportId reportId,
                                 final Long startTs,
                                 final Long endTs,
                                 final Exception exception) {
        auditLogService.logEntityAction(currentUser.getTenantId(),
                currentUser.getCustomerId(),
                currentUser.getId(),
                currentUser.getName(),
                Optional.ofNullable(device).map(Device::getId).orElse(null),
                device, ActionType.REPORT_GENERATED, exception, ReportGenerationAuditLogData.builder()
                        .reportId(reportId.name())
                        .reportName(reportId.getDisplayName())
                        .startTimeInMs(startTs)
                        .endTimeInMs(endTs)
                        .build());
    }


    @ApiOperation(value = "Pdf Report of Alarms (getAlarmsPdfReport)",
            notes = "Returns a pdf report of alarms for the selected entity. " +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/alarm/pdf/{entityType}/{entityId}", method = RequestMethod.GET)
    @ResponseBody
    public DeferredResult<ResponseEntity> getAlarmsPdfReport(
            @Parameter(description = ENTITY_TYPE_PARAM_DESCRIPTION, required = true, schema = @Schema(defaultValue = "DEVICE"))
            @PathVariable(ENTITY_TYPE) String strEntityType,
            @Parameter(description = ENTITY_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ENTITY_ID) String strEntityId,
            @Parameter(description = ALARM_QUERY_SEARCH_STATUS_ARRAY_DESCRIPTION, array = @ArraySchema(schema = @Schema(type = "string", allowableValues = {"ANY", "ACTIVE", "CLEARED", "ACK", "UNACK"})))
            @RequestParam(required = false) String[] statusList,
            @Parameter(description = ALARM_QUERY_SEVERITY_ARRAY_DESCRIPTION, array = @ArraySchema(schema = @Schema(type = "string", allowableValues = {"CRITICAL", "MAJOR", "MINOR", "WARNING", "INDETERMINATE"})))
            @RequestParam(required = false) String[] severityList,
            @Parameter(description = ALARM_QUERY_TYPE_ARRAY_DESCRIPTION, array = @ArraySchema(schema = @Schema(type = "string")))
            @RequestParam(required = false) String[] typeList,
            @Parameter(description = ALARM_QUERY_ASSIGNEE_DESCRIPTION)
            @RequestParam(required = false) String assigneeId,
            @Parameter(description = SORT_PROPERTY_DESCRIPTION, schema = @Schema(allowableValues = {"createdTime", "startTs", "endTs", "type", "ackTs", "clearTs", "severity", "status"}))
            @RequestParam(required = false) String sortProperty,
            @Parameter(description = SORT_ORDER_DESCRIPTION, schema = @Schema(allowableValues = {"ASC", "DESC"}))
            @RequestParam(required = false) String sortOrder,
            @Parameter(description = ALARM_QUERY_START_TIME_DESCRIPTION)
            @RequestParam(required = false) Long startTime,
            @Parameter(description = ALARM_QUERY_END_TIME_DESCRIPTION)
            @RequestParam(required = false) Long endTime,
            @Parameter(schema = @Schema(allowableValues = {"GRANULES_ALARM_REPORT"}))
            @RequestParam(name = "reportId") String reportId
    ) throws ThingsboardException {
        checkParameter("EntityId", strEntityId);
        checkParameter("EntityType", strEntityType);
        EntityId entityId = EntityIdFactory.getByTypeAndId(strEntityType, strEntityId);
        checkEntityId(entityId, Operation.READ);
        List<AlarmSearchStatus> alarmStatusList = new ArrayList<>();
        if (statusList != null) {
            for (String strStatus : statusList) {
                if (!StringUtils.isEmpty(strStatus)) {
                    alarmStatusList.add(AlarmSearchStatus.valueOf(strStatus));
                }
            }
        }
        List<AlarmSeverity> alarmSeverityList = new ArrayList<>();
        if (severityList != null) {
            for (String strSeverity : severityList) {
                if (!StringUtils.isEmpty(strSeverity)) {
                    alarmSeverityList.add(AlarmSeverity.valueOf(strSeverity));
                }
            }
        }
        List<String> alarmTypeList = typeList != null ? Arrays.asList(typeList) : Collections.emptyList();
        UserId assigneeUserId = null;
        if (assigneeId != null) {
            assigneeUserId = new UserId(UUID.fromString(assigneeId));
        }
        return getAlarmsPdfReport(ReportId.valueOf(reportId), 10000, sortProperty, sortOrder, startTime, endTime,
                alarmTypeList, alarmStatusList, alarmSeverityList, assigneeUserId, entityId);
    }

    private DeferredResult<ResponseEntity> getAlarmsPdfReport(final ReportId reportId,
                                                              final long limit, final String sortProperty,
                                                              final String sortOrder, final long startTime, final long endTime,
                                                              final List<String> alarmTypeList, final List<AlarmSearchStatus> alarmStatusList,
                                                              final List<AlarmSeverity> alarmSeverityList, final UserId assigneeUserId,
                                                              final EntityId entityId)
            throws ThingsboardException {

        final SecurityUser currentUser = getCurrentUser();
        return accessValidator.validateEntityAndCallback(currentUser, Operation.READ, entityId.getEntityType().name(),
                entityId.getId().toString(), (result, tenantId, eId) -> {
                    Futures.addCallback(attributesService.findAll(currentUser.getTenantId(), entityId, AttributeScope.SHARED_SCOPE),
                            new FutureCallback<>() {
                                @Override
                                public void onSuccess(final List<AttributeKvEntry> attributeKvEntries) {
                                    try {
                                        if (entityId.getEntityType() != EntityType.DEVICE) {
                                            throw new IllegalArgumentException("Only device entities are supported!");
                                        }
                                        final Device device = deviceService.findDeviceById(currentUser.getTenantId(),
                                                (DeviceId) entityId);
                                        result.setResult(generateAlarmsPdfReportCallback(currentUser, reportId, limit,
                                                sortProperty,
                                                sortOrder, startTime, endTime, alarmTypeList, alarmStatusList, alarmSeverityList,
                                                assigneeUserId, entityId, device, attributeKvEntries));

                                    } catch (final Exception e) {
                                        log.error("Failed to generate pdf report", e);
                                        AccessValidator.handleError(e, result, HttpStatus.INTERNAL_SERVER_ERROR);
                                    }
                                }

                                @Override
                                public void onFailure(final Throwable e) {
                                    log.error("Failed to fetch device data", e);
                                    AccessValidator.handleError(e, result, HttpStatus.INTERNAL_SERVER_ERROR);
                                }
                            }, MoreExecutors.directExecutor());
                });

    }

    private ResponseEntity<byte[]> generateAlarmsPdfReportCallback(
            final SecurityUser currentUser,
            final ReportId reportId,
            final long limit,
            final String sortProperty,
            final String sortOrder,
            final long startTime,
            final long endTime,
            final List<String> alarmTypeList,
            final List<AlarmSearchStatus> alarmStatusList,
            final List<AlarmSeverity> alarmSeverityList,
            final UserId assigneeUserId,
            final EntityId entityId,
            final Device device,
            final List<AttributeKvEntry> attributeKvEntries)
            throws ThingsboardException {

        TimePageLink pageLink = createTimePageLink(DEFAULT_PAGE_SIZE, 0, null, sortProperty, sortOrder, startTime,
                endTime);
        final PageData<AlarmInfo> alarms = alarmService.findAlarmsV2(currentUser.getTenantId(),
                new AlarmQueryV2(entityId, pageLink,
                        alarmTypeList, alarmStatusList, alarmSeverityList, assigneeUserId));
        final List<AlarmInfo> alarmsList = new ArrayList<>(alarms.getData());
        while (alarms.hasNext() && alarms.getTotalElements() < limit) {
            pageLink = pageLink.nextPageLink();
            alarmsList.addAll(alarmService.findAlarmsV2(currentUser.getTenantId(),
                    new AlarmQueryV2(entityId, pageLink,
                            alarmTypeList, alarmStatusList, alarmSeverityList, assigneeUserId)).getData());
        }
        final byte[] pdf = pdfGeneratorFactory.getGenerator(PdfType.valueOf(reportId.name()))
                .generatePdf(UUID.randomUUID().toString(),
                        GranulesAlarmPdfGenerationContext.builder()
                                .tenantId(currentUser.getTenantId())
                                .reportId(reportId.name())
                                .entityId(device.getId())
                                .entityName(device.getName())
                                .userId(currentUser.getId())
                                .userName(currentUser.getName())
                                .startTs(startTime)
                                .endTs(endTime)
                                .sharedAttributes(attributeKvEntries)
                                .alarmDataList(alarmsList)
                                .build());
        publishAuditLog(currentUser, device, reportId, startTime, endTime, null);

        return ResponseEntity.ok(pdf);
    }

    @Getter
    public enum ReportId {
        GRANULES_DEVICE_TIMESERIES_REPORT("Temperature & Humidity Report"),
        GRANULES_ALARM_REPORT("Alarms Report");

        private final String displayName;

        ReportId(String displayName) {
            this.displayName = displayName;
        }
    }
}
