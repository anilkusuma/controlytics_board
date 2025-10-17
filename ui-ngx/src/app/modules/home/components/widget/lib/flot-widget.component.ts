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

import { Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { WidgetContext } from '@home/models/widget-component.models';
import { ChartType, TbFlotSettings } from '@home/components/widget/lib/flot-widget.models';
import { TbFlot } from '@home/components/widget/lib/flot-widget';
import {
  defaultLegendConfig,
  LegendConfig,
  LegendData,
  LegendPosition,
  widgetType
} from '@shared/models/widget.models';
import { isDefinedAndNotNull } from '@core/utils';
import {
  ReLoginDialogComponentData,
  ReLoginDialogComponentResponse
} from '@home/dialogs/re-login/relogin-dialog.component';

@Component({
  selector: 'tb-flot-widget',
  templateUrl: './flot-widget.component.html',
  styleUrls: []
})
export class FlotWidgetComponent implements OnInit {

  @ViewChild('flotElement', {static: true}) flotElement: ElementRef;

  @Input()
  ctx: WidgetContext;

  @Input()
  chartType: ChartType;

  displayLegend: boolean;
  legendConfig: LegendConfig;
  legendData: LegendData;
  isLegendFirst: boolean;
  legendContainerLayoutType: string;
  legendStyle: {[klass: string]: any};

  public settings: TbFlotSettings;
  private flot: TbFlot;

  constructor() {
  }

  ngOnInit(): void {
    this.ctx.$scope.flotWidget = this;
    // Register the downloadTrendReport method on the context
    (this.ctx as any).downloadTrendReport = this.downloadTrendReport.bind(this);
    this.settings = this.ctx.settings;
    this.chartType = this.chartType || 'line';
    this.configureLegend();
    if (this.ctx.datasources?.length) {
      this.flot = new TbFlot(this.ctx, this.chartType, $(this.flotElement.nativeElement));
    }
  }

  private configureLegend(): void {

    this.displayLegend = isDefinedAndNotNull(this.settings.showLegend) ? this.settings.showLegend
      : false;

    this.legendContainerLayoutType = 'column';

    if (this.displayLegend) {
      this.legendConfig = this.settings.legendConfig || defaultLegendConfig(widgetType.timeseries);
      if (this.ctx.defaultSubscription) {
        this.legendData = this.ctx.defaultSubscription.legendData;
      } else {
        this.legendData = {
          keys: [],
          data: []
        };
      }
      if (this.legendConfig.position === LegendPosition.top ||
        this.legendConfig.position === LegendPosition.bottom) {
        this.legendContainerLayoutType = 'column';
        this.isLegendFirst = this.legendConfig.position === LegendPosition.top;
      } else {
        this.legendContainerLayoutType = 'row';
        this.isLegendFirst = this.legendConfig.position === LegendPosition.left;
      }
      switch (this.legendConfig.position) {
        case LegendPosition.top:
          this.legendStyle = {
            paddingBottom: '8px',
            maxHeight: '50%',
            overflowY: 'auto'
          };
          break;
        case LegendPosition.bottom:
          this.legendStyle = {
            paddingTop: '8px',
            maxHeight: '50%',
            overflowY: 'auto'
          };
          break;
        case LegendPosition.left:
          this.legendStyle = {
            paddingRight: '0px',
            maxWidth: '50%',
            overflowY: 'auto'
          };
          break;
        case LegendPosition.right:
          this.legendStyle = {
            paddingLeft: '0px',
            maxWidth: '50%',
            overflowY: 'auto'
          };
          break;
      }
    }
  }

  public onLegendKeyHiddenChange(index: number) {
    for (const id of Object.keys(this.ctx.subscriptions)) {
      const subscription = this.ctx.subscriptions[id];
      subscription.updateDataVisibility(index);
    }
  }

  public onDataUpdated() {
    this.flot.update();
  }

  public onLatestDataUpdated() {
    this.flot.latestDataUpdate();
  }

  public onResize() {
    this.flot.resize();
  }

  public onEditModeChanged() {
    this.flot.checkMouseEvents();
  }

  public onDestroy() {
    this.flot.destroy();
  }

  public downloadTrendReport(title?: string) {
    if (!this.flot) {
      this.ctx.showErrorToast('Chart not initialized');
      return;
    }

    // Show re-authentication dialog
    this.ctx.dialogs.relogin({
      remarksRequired: false,
      intervalRequired: false,
      timeRangeRequired: false,
      userNameInputRequired: false
    } as ReLoginDialogComponentData).subscribe(
      (result: ReLoginDialogComponentResponse) => {
        if (result && result.reloginStatus) {
          // Proceed with PDF generation after successful re-authentication
          this.generateAndDownloadPdf(title, null);
        } else if (result && !result.reloginStatus) {
          this.ctx.showErrorToast('Authentication failed. Please try again.');
        }
      }
    );
  }

  private generateAndDownloadPdf(title: string, remarks: string | null) {
    try {
      // Get the chart as base64 image
      const chartImageBase64 = this.flot.getChartAsBase64();

      // Remove data URL prefix
      const base64Data = chartImageBase64.split(',')[1];

      // Format dates and times
      const startDate = new Date(this.ctx.defaultSubscription.timeWindow.minTime);
      const endDate = new Date(this.ctx.defaultSubscription.timeWindow.maxTime);

      const formatDate = (date: Date) => {
        const day = String(date.getDate()).padStart(2, '0');
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const year = date.getFullYear();
        return `${day}-${month}-${year}`;
      };

      const formatTime = (date: Date) => {
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${hours}:${minutes}`;
      };

      // Get the actual entity name from datasources or subscription
      let entityName = 'Device';
      if (this.ctx.datasources && this.ctx.datasources.length > 0) {
        // Try to get from datasource
        entityName = this.ctx.datasources[0].entityName || this.ctx.datasources[0].name || entityName;
      } else if (this.ctx.defaultSubscription?.targetEntityName) {
        // Fallback to subscription target entity name
        entityName = this.ctx.defaultSubscription.targetEntityName;
      }

      // Prepare chart title
      const chartTitle = title || 'Temperature and Humidity Trend';

      // Prepare report data
      const reportData = {
        entityId: this.ctx.defaultSubscription?.datasources[0]?.entityId || '',
        entityType: this.ctx.defaultSubscription?.datasources[0]?.entityType || '',
        entityName,
        chartTitle,
        chartImageBase64: base64Data,
        startDate: formatDate(startDate),
        startTime: formatTime(startDate),
        endDate: formatDate(endDate),
        endTime: formatTime(endDate)
      };

      // Call backend API to generate PDF
      this.ctx.http.post('/api/reports/trend', reportData, { responseType: 'blob' }).subscribe(
        (response: Blob) => {
          // Download the PDF
          const url = window.URL.createObjectURL(response);
          const link = document.createElement('a');
          link.href = url;
          // Use chart title with underscores for filename (all lowercase)
          const safeFilename = chartTitle.toLowerCase().replace(/\s+/g, '_').replace(/[^a-z0-9_-]/g, '');
          const timestamp = new Date().getTime();
          link.download = `${safeFilename}_${timestamp}.pdf`;
          document.body.appendChild(link);
          link.click();
          document.body.removeChild(link);
          window.URL.revokeObjectURL(url);
          this.ctx.showSuccessToast('Trend report downloaded successfully');
        },
        error => {
          console.error('Error generating trend report:', error);
          this.ctx.showErrorToast('Failed to generate trend report');
        }
      );
    } catch (error) {
      console.error('Error capturing chart image:', error);
      this.ctx.showErrorToast('Failed to capture chart image');
    }
  }

}
