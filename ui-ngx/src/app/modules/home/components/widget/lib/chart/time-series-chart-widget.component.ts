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
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  Input,
  OnDestroy,
  OnInit,
  Renderer2,
  TemplateRef,
  ViewChild,
  ViewEncapsulation
} from '@angular/core';
import {
  timeSeriesChartKeyDefaultSettings,
  TimeSeriesChartKeySettings
} from '@home/components/widget/lib/chart/time-series-chart.models';
import { WidgetContext } from '@home/models/widget-component.models';
import { Observable } from 'rxjs';
import { backgroundStyle, ComponentStyle, overlayStyle, textStyle } from '@shared/models/widget-settings.models';
import { ImagePipe } from '@shared/pipe/image.pipe';
import { DomSanitizer } from '@angular/platform-browser';
import { LegendConfig, LegendData, LegendKey, LegendPosition } from '@shared/models/widget.models';
import { TbTimeSeriesChart } from '@home/components/widget/lib/chart/time-series-chart';
import {
  timeSeriesChartWidgetDefaultSettings,
  TimeSeriesChartWidgetSettings
} from '@home/components/widget/lib/chart/time-series-chart-widget.models';
import { mergeDeep } from '@core/utils';
import {
  ReLoginDialogComponentData,
  ReLoginDialogComponentResponse
} from '@home/dialogs/re-login/relogin-dialog.component';

@Component({
  selector: 'tb-time-series-chart-widget',
  templateUrl: './time-series-chart-widget.component.html',
  styleUrls: ['./time-series-chart-widget.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class TimeSeriesChartWidgetComponent implements OnInit, OnDestroy, AfterViewInit {

  @ViewChild('chartShape', {static: false})
  chartShape: ElementRef<HTMLElement>;

  settings: TimeSeriesChartWidgetSettings;

  @Input()
  ctx: WidgetContext;

  @Input()
  widgetTitlePanel: TemplateRef<any>;

  horizontalLegendPosition = false;

  showLegend: boolean;
  legendClass: string;
  legendConfig: LegendConfig;
  legendData: LegendData;
  legendKeys: LegendKey[];

  backgroundStyle$: Observable<ComponentStyle>;
  overlayStyle: ComponentStyle = {};
  padding: string;

  legendColumnTitleStyle: ComponentStyle;
  legendLabelStyle: ComponentStyle;
  disabledLegendLabelStyle: ComponentStyle;
  legendValueStyle: ComponentStyle;

  displayLegendValues = false;

  private timeSeriesChart: TbTimeSeriesChart;

  constructor(private imagePipe: ImagePipe,
              private sanitizer: DomSanitizer,
              private renderer: Renderer2,
              private cd: ChangeDetectorRef) {
  }

  ngOnInit(): void {
    // Register the widget component for custom actions
    if (!this.ctx.$scope) {
      this.ctx.$scope = {} as any;
    }
    this.ctx.$scope.timeSeriesChartWidget = this;
    // Also add directly to context for easier access
    (this.ctx as any).downloadTrendReport = this.downloadTrendReport.bind(this);
    this.settings = {...timeSeriesChartWidgetDefaultSettings, ...this.ctx.settings};

    this.backgroundStyle$ = backgroundStyle(this.settings.background, this.imagePipe, this.sanitizer);
    this.overlayStyle = overlayStyle(this.settings.background.overlay);
    this.padding = this.settings.background.overlay.enabled ? undefined : this.settings.padding;

    this.showLegend = this.settings.showLegend;
    if (this.showLegend) {
      this.legendData = this.ctx.defaultSubscription.legendData;
      this.legendConfig = this.settings.legendConfig;
      this.legendKeys = this.legendData.keys;
      if (this.legendConfig.sortDataKeys) {
        this.legendKeys = this.legendData.keys.sort((key1, key2) => key1.dataKey.label.localeCompare(key2.dataKey.label));
      }
      this.legendKeys.forEach(legendKey => {
        legendKey.dataKey.settings = mergeDeep<TimeSeriesChartKeySettings>({} as TimeSeriesChartKeySettings,
          timeSeriesChartKeyDefaultSettings, legendKey.dataKey.settings);
        legendKey.dataKey.hidden = legendKey.dataKey.settings.dataHiddenByDefault;
      });
      this.legendKeys = this.legendKeys.filter(legendKey => legendKey.dataKey.settings.showInLegend);
      if (!this.legendKeys.length) {
        this.showLegend = false;
      }
    }

    if (this.showLegend) {
      this.horizontalLegendPosition = [LegendPosition.left, LegendPosition.right].includes(this.legendConfig.position);
      this.legendClass = `legend-${this.legendConfig.position}`;
      this.legendColumnTitleStyle = textStyle(this.settings.legendColumnTitleFont);
      this.legendColumnTitleStyle.color = this.settings.legendColumnTitleColor;
      this.legendLabelStyle = textStyle(this.settings.legendLabelFont);
      this.disabledLegendLabelStyle = textStyle(this.settings.legendLabelFont);
      this.legendLabelStyle.color = this.settings.legendLabelColor;
      this.legendValueStyle = textStyle(this.settings.legendValueFont);
      this.legendValueStyle.color = this.settings.legendValueColor;
      this.displayLegendValues = this.legendConfig.showMin || this.legendConfig.showMax ||
        this.legendConfig.showAvg || this.legendConfig.showTotal || this.legendConfig.showLatest;
    }
  }

  ngAfterViewInit() {
    this.timeSeriesChart = new TbTimeSeriesChart(this.ctx, this.settings, this.chartShape.nativeElement, this.renderer);
  }

  ngOnDestroy() {
    if (this.timeSeriesChart) {
      this.timeSeriesChart.destroy();
    }
  }

  public onInit() {
    const borderRadius = this.ctx.$widgetElement.css('borderRadius');
    this.overlayStyle = {...this.overlayStyle, ...{borderRadius}};
    this.cd.detectChanges();
  }

  public onDataUpdated() {
    if (this.timeSeriesChart) {
      this.timeSeriesChart.update();
    }
  }

  public onLatestDataUpdated() {
    if (this.timeSeriesChart) {
      this.timeSeriesChart.latestUpdated();
    }
  }

  public onLegendKeyEnter(legendKey: LegendKey) {
    this.timeSeriesChart.keyEnter(legendKey.dataKey);
  }

  public onLegendKeyLeave(legendKey: LegendKey) {
    this.timeSeriesChart.keyLeave(legendKey.dataKey);
  }

  public toggleLegendKey(legendKey: LegendKey) {
    this.timeSeriesChart.toggleKey(legendKey.dataKey);
  }

  public downloadTrendReport(title?: string) {

    if (!this.timeSeriesChart) {
      this.ctx.showErrorToast('Chart not initialized');
      return;
    }

    const chartInstance = this.timeSeriesChart.getChartInstance();
    if (!chartInstance) {
      this.ctx.showErrorToast('Unable to access chart instance');
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
          this.generateAndDownloadPdf(title, chartInstance, null);
        } else if (result && !result.reloginStatus) {
          this.ctx.showErrorToast('Authentication failed. Please try again.');
        }
      }
    );
  }

  private generateAndDownloadPdf(title: string, chartInstance: any, remarks: string | null) {
    // Capture chart as base64 image with higher resolution
    const chartImageBase64 = chartInstance.getDataURL({
      type: 'png',
      pixelRatio: 3, // Increased for better quality
      backgroundColor: '#fff'
    });

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
  }
}
