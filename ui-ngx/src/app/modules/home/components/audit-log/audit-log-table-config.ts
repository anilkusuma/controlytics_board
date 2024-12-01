///
/// Copyright © 2016-2024 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import {
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import {
  actionStatusTranslations,
  actionTypeTranslations,
  AuditLog,
  AuditLogMode
} from '@shared/models/audit-log.models';
import {EntityType, EntityTypeResource, entityTypeTranslations} from '@shared/models/entity-type.models';
import {AuditLogService} from '@core/http/audit-log.service';
import {TranslateService} from '@ngx-translate/core';
import {DatePipe} from '@angular/common';
import {Direction} from '@shared/models/page/sort-order';
import {MatDialog} from '@angular/material/dialog';
import {PageLink, TimePageLink} from '@shared/models/page/page-link';
import {Observable} from 'rxjs';
import {PageData} from '@shared/models/page/page-data';
import {EntityId} from '@shared/models/id/entity-id';
import {UserId} from '@shared/models/id/user-id';
import {CustomerId} from '@shared/models/id/customer-id';
import {
  AuditLogDetailsDialogComponent,
  AuditLogDetailsDialogData
} from '@home/components/audit-log/audit-log-details-dialog.component';
import {
  ReloginDialogComponent,
  ReLoginDialogComponentData, ReLoginDialogComponentResponse
} from "@home/dialogs/re-login/relogin-dialog.component";
import {filter, map} from "rxjs/operators";

export class AuditLogTableConfig extends EntityTableConfig<AuditLog, TimePageLink> {

  constructor(private auditLogService: AuditLogService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dialog: MatDialog,
              private auditLogMode: AuditLogMode = AuditLogMode.TENANT,
              public entityId: EntityId = null,
              public userId: UserId = null,
              public customerId: CustomerId = null,
              updateOnInit = true,
              pageMode = false) {
    super();
    this.loadDataOnInit = updateOnInit;
    this.tableTitle = '';
    this.useTimePageLink = true;
    this.pageMode = pageMode;
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.searchEnabled = true;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;
    //this.actionsColumnTitle = 'audit-log.details';
    this.entityTranslations = {
      noEntities: 'audit-log.no-audit-logs-prompt',
      search: 'audit-log.search'
    };
    this.entityResources = {} as EntityTypeResource<AuditLog>;

    this.entitiesFetchFunction = pageLink => this.fetchAndFilterAuditLogs(pageLink);

    this.defaultSortOrder = {property: 'createdTime', direction: Direction.DESC};

    this.columns.push(
      new DateEntityTableColumn<AuditLog>('createdTime', 'audit-log.timestamp', this.datePipe, '150px'));

    if (this.auditLogMode !== AuditLogMode.USER) {
      this.columns.push(
        new EntityTableColumn<AuditLog>('userName', 'audit-log.user', '20%')
      );
    }

    this.columns.push(
      new EntityTableColumn<AuditLog>('details', 'audit-log.details', '33%',
        (entity) => this.getDetailsString(entity),
        () => ({}), false)
    );

    this.columns.push(
      new EntityTableColumn<AuditLog>('actionType', 'audit-log.type', '20%',
        (entity) => translate.instant(actionTypeTranslations.get(entity.actionType))),
      new EntityTableColumn<AuditLog>('actionStatus', 'audit-log.status', '10%',
        (entity) => translate.instant(actionStatusTranslations.get(entity.actionStatus)))
    );

    if (this.auditLogMode === AuditLogMode.TENANT) {
      this.headerActionDescriptors.push({
        name: this.translate.instant('audit-log.download-audit-logs'),
        icon: 'mdi:download',
        isEnabled: () => true,
        onAction: ($event) => {
          this.dialog.open<ReloginDialogComponent, ReLoginDialogComponentData, ReLoginDialogComponentResponse>(ReloginDialogComponent, {
            disableClose: true,
            panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
            data: <ReLoginDialogComponentData> {
              remarksRequired: false,
              timeRangeRequired: false,
              intervalRequired: false
            },
          }).afterClosed().subscribe(
            (result) => {
              if (result?.reloginStatus) {
                console.log('Relogin successful, with remarks: ' + result.remarks);
                this.downloadAuditLogs(this.getTable().pageLink, result.remarks).subscribe(
                  (blob) => {
                    const blobUrl = window.URL.createObjectURL(blob);
                    const link = document.createElement('a');
                    link.href = blobUrl;
                    link.download = `audit_logs_${this.datePipe.transform(new Date(), 'dd_MM_yyyy_HH_mm_ss')}.pdf`;
                    link.click();
                  }
                );
              } else {
                console.log('Relogin failed');
              }
            }
          );
        }
      });
    }

    // this.cellActionDescriptors.push(
    //   {
    //     name: this.translate.instant('audit-log.details'),
    //     icon: 'more_horiz',
    //     isEnabled: () => true,
    //     onAction: ($event, entity) => this.showAuditLogDetails(entity)
    //   }
    // );
  }

  fetchAuditLogs(pageLink: TimePageLink): Observable<PageData<AuditLog>> {
    switch (this.auditLogMode) {
      case AuditLogMode.TENANT:
        return this.auditLogService.getAuditLogs(pageLink);
      case AuditLogMode.ENTITY:
        return this.auditLogService.getAuditLogsByEntityId(this.entityId, pageLink);
      case AuditLogMode.USER:
        return this.auditLogService.getAuditLogsByUserId(this.userId.id, pageLink);
      case AuditLogMode.CUSTOMER:
        return this.auditLogService.getAuditLogsByCustomerId(this.customerId.id, pageLink);
    }
  }


  fetchAndFilterAuditLogs(pageLink: TimePageLink): Observable<PageData<AuditLog>> {
    return this.fetchAuditLogs(pageLink).pipe(
      map((pageData) => {
        pageData.data = pageData.data.filter((auditLog) => {
          return auditLog.actionType != 'ADDED_COMMENT'
        });
        return pageData;
      })
    );
  }


  downloadAuditLogs(pageLink: PageLink, remarks: string): Observable<Blob> {
    switch (this.auditLogMode) {
      case AuditLogMode.TENANT:
        return this.auditLogService.getAuditLogsPdf(pageLink, remarks);
    }
  }

  getDetailsString(entity: AuditLog): string {
    if (entity.actionType == 'ALARM_ACK' || entity.actionType == 'ALARM_ASSIGNED'
      || entity.actionType == 'ALARM_CLEAR' || entity.actionType == 'ALARM_UNASSIGNED') {
      return 'Alarm: ' + entity.entityName + '<br>' + this.getSubDetailsString(entity);
    }
    return entity.entityId.entityType + ':' + entity.entityName + '<br>' + this.getSubDetailsString(entity);
  }

  getSubDetailsString(entity: AuditLog): string {
    switch (entity.actionType) {
      case 'ATTRIBUTES_DELETED':
        return entity.actionData?.['attributes']?.join(', ') || '';
      case 'ATTRIBUTES_READ':
        return entity.actionData?.['attributes']?.join(', ') || '';
      case 'ATTRIBUTES_UPDATED':
        let result = '';
        if (entity.actionData?.attributes) {
          console.log(entity.actionData.attributes);
          for (const [key, value] of Object.entries(entity.actionData.attributes)) {
            const attributeValue = value as any;
            console.log(attributeValue);
            if (attributeValue instanceof Object && attributeValue['old_value']) {
              result += `${key}: Changed FROM: ${attributeValue['old_value']} TO: ${attributeValue['new_value']}. <br>`;
            } else if (attributeValue instanceof Object && attributeValue['old_value'] == undefined) {
              result += `${key}: Added: ${attributeValue['new_value']}. <br>`;
            } else if (attributeValue instanceof Array) {
              result += `${key}: ${attributeValue.join(', ')}. <br>`;
            } else if (attributeValue !== null) {
              result += `${key}: Changed TO: ${attributeValue.toString()}. <br>`;
            }
          }
          if (entity.actionData?.remarks) {
            result += `Remarks: ${entity.actionData.remarks}`;
          }
        }
        return result;
      case 'LOGIN':
        return entity.actionData?.['clientAddress']?.trim() ? `IP: ${entity.actionData['clientAddress']}` : '';
      case 'LOGOUT':
        return entity.actionData?.['clientAddress']?.trim() ? `IP: ${entity.actionData['clientAddress']}` : '';
      case 'LOCKOUT':
        return entity.actionData?.['clientAddress']?.trim() ? `IP: ${entity.actionData['clientAddress']}` : '';
      case 'ALARM_ACK':
      case 'ALARM_ASSIGNED':
      case 'ALARM_CLEAR':
      case 'ALARM_UNASSIGNED':
        return (entity.actionData?.['entity']?.['originatorName'] ? `Device : ${entity.actionData['entity']['originatorName']} <br>` : '')
          + (entity.actionData?.['entity']?.['details']?.['createdValue'] ? `Created Value: ${entity.actionData['entity']['details']['createdValue']} <br>` : '')
          + (entity.actionData?.['entity']?.['details']?.['clearedValue'] ? `Cleared Value: ${entity.actionData['entity']['details']['clearedValue']} <br>` : '')

      case 'REPORT_GENERATED':
        return (entity.actionData?.['reportName'] ? `Report: ${entity.actionData['reportName']} <br>` : '') +
          (entity.actionData?.['startTime'] ? `Start Time: ${entity.actionData['startTime']} <br>`  : '') +
          (entity.actionData?.['endTime'] ? `End Time: ${entity.actionData['endTime']} <br>` : '');
      default:
        return '';
    }
  }

  showAuditLogDetails(entity: AuditLog) {
    this.dialog.open<AuditLogDetailsDialogComponent, AuditLogDetailsDialogData>(AuditLogDetailsDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        auditLog: entity
      }
    });
  }

}
