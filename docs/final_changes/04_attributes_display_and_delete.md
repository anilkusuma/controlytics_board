# Requirement #4: Attributes Display Format and Delete Option for Controlytics Admin

## Requirement

For Controlytics Admin users:
1. **Display Issue**: Attribute values are not displaying properly - showing JSON/technical format instead of clean display
2. **Delete Functionality**: Delete option should be available for attributes

## Current Implementation as per Code

### 1. Attribute Value Display

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.html`

**Lines 188**:
```html
<span class="ellipsis">{{attribute.value | tbJson}}</span>
```

**Pipe Implementation** (`/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/shared/pipe/tbJson.pipe.ts`):
```typescript
@Pipe({name: 'tbJson'})
export class TbJsonPipe implements PipeTransform {
  transform(value: any): string {
    if (isObject(value)) {
      return JSON.stringify(value);  // This converts objects to JSON string
    } else if (isNumber(value)) {
      return value.toString();
    }
    return value;
  }
}
```

**Issue**: The `tbJson` pipe converts object values to JSON string format using `JSON.stringify()`, which results in technical/raw JSON format display like `{"key":"value"}` instead of a clean formatted display.

### 2. Edit Button Display Issue

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.html`

**Lines 200-206**:
```html
<span [fxShow]="!isClientSideTelemetryTypeMap.get(attributeScope) && userRole === UserRole.CONTROLYTICS_ADMIN">
      class="tb-icon-button"
      matTooltip="{{ 'action.edit' | translate }}"
      matTooltipPosition="above"
      (click)="editAttribute($event, attribute)">
  <mat-icon>edit</mat-icon>
</span>
```

**Issue**: The `<span>` element is missing proper structure. The `class` attribute and other attributes are not properly enclosed within the opening tag.

### 3. Delete Functionality

**Bulk Delete Button** (Lines 88-96):
```html
<button [fxShow]="false"
        *ngIf="userRole === UserRole.CONTROLYTICS_ADMIN"
        class="button-widget-action"
        mat-icon-button [disabled]="isLoading$ | async"
        matTooltip="{{ 'action.delete' | translate }}"
        matTooltipPosition="above"
        (click)="deleteTelemetry($event)">
  <mat-icon>delete</mat-icon>
</button>
```

**Individual Delete Button** (Lines 207-210):
```html
<button mat-icon-button *ngIf="false"
        (click)="deleteTimeseries($event, attribute)">
  <mat-icon>delete</mat-icon>
</button>
```

**Current State**:
- Both delete buttons exist in the code
- Bulk delete button: Hidden with `[fxShow]="false"` and restricted to CONTROLYTICS_ADMIN
- Individual delete button: Hidden with `*ngIf="false"` (no role restriction)
- Backend delete API exists and is functional (`deleteEntityAttributes` and `deleteEntityTimeseries` methods)

### 4. Backend Support

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/TelemetryController.java`

**Attribute Retrieval** (Lines 796-816):
```java
private FutureCallback<List<AttributeKvEntry>> getAttributeValuesToResponseCallback(...) {
    return new FutureCallback<>() {
        @Override
        public void onSuccess(List<AttributeKvEntry> attributes) {
            List<AttributeData> values = attributes.stream().map(attribute ->
                    new AttributeData(attribute.getLastUpdateTs(), attribute.getKey(), getKvValue(attribute))
            ).collect(Collectors.toList());
            response.setResult(new ResponseEntity<>(values, HttpStatus.OK));
        }
    };
}

private Object getKvValue(KvEntry entry) {
    if (entry.getDataType() == DataType.JSON) {
        return toJsonNode(entry.getJsonValue().get());  // Returns JsonNode for JSON types
    }
    return entry.getValue();
}
```

**Backend Behavior**: The backend properly returns JSON objects as JsonNode for JSON-type attributes, which when serialized and received by the frontend, become JavaScript objects.

## Expectation after the Change

### 1. Attribute Value Display
- Attribute values should display in a clean, readable format for Controlytics Admin
- For simple values (strings, numbers, booleans): Display as-is without quotes
- For object/JSON values: Display in a formatted, readable way (either pretty-printed JSON or extract key properties)
- Consider two approaches:
  - **Option A**: Create a role-aware pipe that formats values differently for Controlytics Admin
  - **Option B**: Modify the `tbJson` pipe to handle object values more gracefully with formatted display

### 2. Edit Button
- The edit button should display properly for Controlytics Admin
- Fix the HTML syntax error in the span element

### 3. Delete Functionality
- Enable the bulk delete button for Controlytics Admin by changing `[fxShow]="false"` to `[fxShow]="true"` or removing the attribute
- Optionally enable the individual delete button per row for finer control
- Delete functionality should work for both attributes and timeseries based on the selected scope

## Changes Required

### Change 1: Fix Edit Button Syntax Error

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.html`

**Current Code** (Lines 200-206):
```html
<span [fxShow]="!isClientSideTelemetryTypeMap.get(attributeScope) && userRole === UserRole.CONTROLYTICS_ADMIN">
      class="tb-icon-button"
      matTooltip="{{ 'action.edit' | translate }}"
      matTooltipPosition="above"
      (click)="editAttribute($event, attribute)">
  <mat-icon>edit</mat-icon>
</span>
```

**Changed Code**:
```html
<span [fxShow]="!isClientSideTelemetryTypeMap.get(attributeScope) && userRole === UserRole.CONTROLYTICS_ADMIN"
      class="tb-icon-button"
      matTooltip="{{ 'action.edit' | translate }}"
      matTooltipPosition="above"
      (click)="editAttribute($event, attribute)">
  <mat-icon>edit</mat-icon>
</span>
```

### Change 2: Enable Delete Button for Controlytics Admin

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.html`

**Current Code** (Lines 88-96):
```html
<button [fxShow]="false"
        *ngIf="userRole === UserRole.CONTROLYTICS_ADMIN"
```

**Changed Code**:
```html
<button [fxShow]="true"
        *ngIf="userRole === UserRole.CONTROLYTICS_ADMIN"
```

**OR** simply remove the `[fxShow]` directive entirely:
```html
<button *ngIf="userRole === UserRole.CONTROLYTICS_ADMIN"
```

### Change 3: Improve Attribute Value Display

**Option A - Modify the tbJson Pipe for Better Formatting**:

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/shared/pipe/tbJson.pipe.ts`

**Current Code**:
```typescript
@Pipe({name: 'tbJson'})
export class TbJsonPipe implements PipeTransform {
  transform(value: any): string {
    if (isObject(value)) {
      return JSON.stringify(value);
    } else if (isNumber(value)) {
      return value.toString();
    }
    return value;
  }
}
```

**Changed Code** (Pretty-print with indentation):
```typescript
@Pipe({name: 'tbJson'})
export class TbJsonPipe implements PipeTransform {
  transform(value: any, prettyPrint: boolean = false): string {
    if (isObject(value)) {
      // Pretty print with indentation for better readability
      return JSON.stringify(value, null, prettyPrint ? 2 : 0);
    } else if (isNumber(value)) {
      return value.toString();
    }
    return value;
  }
}
```

**Then update the HTML** to use pretty print:
```html
<span class="ellipsis">{{attribute.value | tbJson:true}}</span>
```

**Option B - Create Role-Aware Display**:

Create a new method in the component to format values based on user role:

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.ts`

Add method:
```typescript
formatAttributeValue(value: any): string {
  if (this.userRole === UserRole.CONTROLYTICS_ADMIN) {
    // For Controlytics Admin, show detailed/technical format
    if (isObject(value)) {
      return JSON.stringify(value, null, 2); // Pretty-printed JSON
    }
  }
  // For other users, use standard pipe
  return value;
}
```

Update HTML to use the method:
```html
<span class="ellipsis">{{formatAttributeValue(attribute.value)}}</span>
```

### Change 4: Optional - Enable Individual Delete Buttons

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.html`

**Current Code** (Lines 207-210):
```html
<button mat-icon-button *ngIf="false"
        (click)="deleteTimeseries($event, attribute)">
  <mat-icon>delete</mat-icon>
</button>
```

**Changed Code** (if individual delete is desired):
```html
<button mat-icon-button
        *ngIf="userRole === UserRole.CONTROLYTICS_ADMIN"
        (click)="deleteTimeseries($event, attribute)"
        matTooltip="{{ 'action.delete' | translate }}"
        matTooltipPosition="above">
  <mat-icon>delete</mat-icon>
</button>
```

## Summary

The issues stem from:
1. **Display Format**: The `tbJson` pipe converts objects to compact JSON strings without formatting, making them hard to read
2. **HTML Syntax Error**: The edit button span has malformed HTML with attributes outside the opening tag
3. **Hidden Delete Button**: The delete functionality exists but is hidden with `[fxShow]="false"`

The fixes involve:
1. Fixing the HTML syntax error for the edit button
2. Enabling the delete button by changing `[fxShow]="false"` to `[fxShow]="true"` or removing it
3. Improving attribute value display by either pretty-printing JSON or creating role-aware formatting

All backend APIs are already in place and functional. Only frontend UI changes are required.
