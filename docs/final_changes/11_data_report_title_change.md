# Requirement 11: Data Report Title Change

## Requirement
In the data report, change the title from "TEMP AND RH LOG REPORT" to "Temperature and RH Data Report".

## Current Implementation as per Code

### 1. HTML Template
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/common/report-gen/src/main/resources/pdf_templates/granules-device-timeseries-report.html`

**Line 161:** The report title is hardcoded in the HTML template:
```html
<h1 class="title">TEMP AND RH LOG REPORT</h1>
```

This title appears in the header section of the PDF report template, within a table cell that is centered and uses bold 22px font.

### 2. Report Generator
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/common/report-gen/src/main/java/org/thingsboard/server/reports/pdfgen/generator/GranulesTimeseriesPdfReportGenerator.java`

This Java class loads the HTML template `granules-device-timeseries-report.html` and generates the PDF report using Thymeleaf template engine.

### 3. Report Metadata
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/PdfReportController.java`

**Line 469:** The report has a display name in the controller:
```java
GRANULES_DEVICE_TIMESERIES_REPORT("Temperature & Humidity Report"),
```

This display name is used for audit logging purposes and represents the report type internally.

### 4. Report Flow
- The report is identified as `PdfType.GRANULES_DEVICE_TIMESERIES_REPORT`
- The `PdfGeneratorFactory` routes to `granulesTimeseriesPdfReportGenerator`
- The generator uses the HTML template to create the PDF with the title "TEMP AND RH LOG REPORT"

## Expectation after the Change

The PDF report header should display:
```
Temperature and RH Data Report
```

Instead of the current:
```
TEMP AND RH LOG REPORT
```

This change will make the report title more professional and descriptive, matching the expected format with proper capitalization and terminology (using "Data Report" instead of "LOG REPORT").

## Changes Required

### Single File Change
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/common/report-gen/src/main/resources/pdf_templates/granules-device-timeseries-report.html`

**Line 161:** Update the title text:

**Current:**
```html
<h1 class="title">TEMP AND RH LOG REPORT</h1>
```

**Change to:**
```html
<h1 class="title">Temperature and RH Data Report</h1>
```

### Notes
- This is a simple text change in the HTML template
- No Java code changes required
- No database changes required
- The change is purely presentational in the PDF output
- The report display name in the controller ("Temperature & Humidity Report") remains unchanged as it serves a different purpose (audit logging)
- No frontend changes needed
- No build or configuration changes required
