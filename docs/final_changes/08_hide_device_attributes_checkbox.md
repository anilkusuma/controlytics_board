# Requirement

**Requirement #8: Hide Checkbox Selection in Device Shared Attributes for Maintenance User**

In the Device details panel, when viewing shared attributes, there are checkboxes that allow users to select individual attributes or all attributes at once. These checkboxes enable bulk operations such as:
- Deleting multiple attributes
- Showing selected attributes on widgets
- Adding selected attributes to dashboards

For Maintenance users specifically, these checkboxes should be hidden to prevent them from performing bulk operations on device attributes.

## Context
- **Screen:** Device Details > Attributes Tab
- **Affected Component:** Attribute table component (tb-attribute-table)
- **Target Role:** Maintenance users only
- **Other Roles:** Admin, Controlytics Admin, Operator, and Supervisor should continue to see checkboxes
- **Functionality to Restrict:** Checkbox selection for bulk attribute operations

---

# Current Implementation as per Code

## 1. Attribute Table Component (TypeScript)
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.ts`

### User Role Initialization
- **Line 139:** User role is stored as a class property:
```typescript
userRole: UserRole;
```

- **Line 213:** User role is retrieved from the auth state during component initialization:
```typescript
this.userRole = getCurrentAuthState(this.store).userDetails.additionalInfo?.role;
```

### Displayed Columns Configuration
- **Line 116:** The `displayedColumns` array is hardcoded to always include the 'select' column:
```typescript
displayedColumns = ['select', 'key', 'value'];
```

This array controls which columns are rendered in the attribute table. Currently, it unconditionally includes the 'select' column for all users, regardless of their role.

### Checkbox Functionality
The checkboxes in the attribute table serve the following purposes:

1. **Delete Attributes/Telemetry (Lines 473-503):**
   - Users can select attributes and delete them
   - Already restricted by role: delete button is only shown for `CONTROLYTICS_ADMIN` (Line 89)

2. **Show on Widget (Lines 505-555, HTML Lines 97-105):**
   - Users can select attributes and visualize them on widgets
   - Enter widget mode when attributes are selected
   - Create datasources from selected attributes
   - Currently available to ALL users who can select checkboxes

3. **Selection State Management:**
   - `dataSource.selection` tracks which attributes are selected
   - Used throughout the component for bulk operations

## 2. Attribute Table Component (HTML)
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.html`

### Checkbox Column Definition (Lines 143-156)
```html
<ng-container matColumnDef="select" sticky>
  <mat-header-cell *matHeaderCellDef style="width: 40px;">
    <mat-checkbox (change)="$event ? dataSource.masterToggle() : null"
                  [checked]="dataSource.selection.hasValue() && (dataSource.isAllSelected() | async)"
                  [indeterminate]="dataSource.selection.hasValue() && !(dataSource.isAllSelected() | async)">
    </mat-checkbox>
  </mat-header-cell>
  <mat-cell *matCellDef="let attribute">
    <mat-checkbox (click)="$event.stopPropagation()"
                  (change)="$event ? dataSource.selection.toggle(attribute) : null"
                  [checked]="dataSource.selection.isSelected(attribute)">
    </mat-checkbox>
  </mat-cell>
</ng-container>
```

- **Header Checkbox (Lines 144-148):** Master checkbox to select/deselect all attributes
- **Row Checkbox (Lines 150-154):** Individual checkbox for each attribute

### Selection Toolbar (Lines 81-107)
When attributes are selected (checkboxes are checked), a special toolbar appears showing:
- Number of selected attributes (Line 84-85)
- Delete button (hidden by `[fxShow]="false"`, but restricted to CONTROLYTICS_ADMIN at Line 89)
- "Show on Widget" button (Lines 97-105) - available to all users

### Table Header and Rows (Lines 214-217)
```html
<mat-header-row [ngClass]="{'mat-row-select': true}" *matHeaderRowDef="displayedColumns; sticky: true"></mat-header-row>
<mat-row [ngClass]="{'mat-row-select': true,
                     'mat-selected': dataSource.selection.isSelected(attribute)}"
         *matRowDef="let attribute; columns: displayedColumns;" (click)="dataSource.selection.toggle(attribute)"></mat-row>
```

The `displayedColumns` array controls which columns are shown. Currently, it always includes 'select', 'key', and 'value' for all users.

## 3. Device Tabs Component
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/device/device-tabs.component.html`

**Lines 18-24:** Device attributes tab implementation:
```html
<mat-tab *ngIf="entity"
         label="{{ 'attribute.attributes' | translate }}" #attributesTab="matTab">
  <tb-attribute-table [defaultAttributeScope]="attributeScopes.CLIENT_SCOPE"
                      [active]="attributesTab.isActive"
                      [entityId]="entity.id"
                      [entityName]="entity.name">
  </tb-attribute-table>
</mat-tab>
```

The attribute table component is used without any role-based restrictions, meaning all users see the same UI.

## 4. User Role Model
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/shared/models/user.model.ts`

**Lines 35-41:** User roles enumeration:
```typescript
export enum UserRole {
  ADMIN = 'admin',
  OPERATOR = 'operator',
  MAINTENANCE = 'maintenance',
  SUPERVISOR = 'supervisor',
  CONTROLYTICS_ADMIN = 'controlytics_admin',
}
```

## 5. Existing Role-Based Restrictions in Attribute Table

The component already has some role-based restrictions:

1. **Add Attribute Button (HTML Line 37):**
   ```html
   *ngIf="attributeScope !== attributeScopeTypes.CLIENT_SCOPE && userRole === UserRole.CONTROLYTICS_ADMIN"
   ```
   Only CONTROLYTICS_ADMIN can add attributes

2. **Edit Attribute Button (HTML Line 200):**
   ```html
   [fxShow]="!isClientSideTelemetryTypeMap.get(attributeScope) && userRole === UserRole.CONTROLYTICS_ADMIN"
   ```
   Only CONTROLYTICS_ADMIN can edit attributes

3. **Attribute Scope Selection (TypeScript Lines 289-294):**
   ```typescript
   if (this.userRole === UserRole.CONTROLYTICS_ADMIN) {
     this.attributeScopes = Object.keys(AttributeScope);
   } else {
     this.attributeScopes = [AttributeScope.SHARED_SCOPE];
   }
   ```
   Non-admin users only see SHARED_SCOPE attributes

---

# Expectation after the change

After implementing the changes, the behavior should be as follows:

## For Maintenance Users:
1. **No Checkbox Column:** The checkbox column should not be visible in the attribute table
2. **No Bulk Selection:** Cannot select multiple attributes
3. **No Selection Toolbar:** The toolbar showing "X attributes selected" should never appear
4. **No Widget Mode:** Cannot access the "Show on Widget" functionality
5. **Read-Only View:** Can only view attribute keys and values
6. **Individual Operations:** Can still use individual attribute actions if permitted (e.g., view, copy)

## For Other Roles (Admin, Controlytics Admin, Operator, Supervisor):
1. **Checkbox Column Visible:** Continue to see checkboxes in the attribute table
2. **Bulk Selection Enabled:** Can select single or multiple attributes
3. **Selection Toolbar Available:** See the selection toolbar with action buttons
4. **Widget Mode Accessible:** Can use "Show on Widget" feature
5. **Full Functionality:** All existing checkbox-related features remain available

## UI Comparison:

**Before (All Users):**
```
┌─────────────────────────────────────────────────┐
│ Shared Attributes                          [+]  │
├──┬──────────────────────────────┬───────────────┤
│☐ │ Key                          │ Value         │
├──┼──────────────────────────────┼───────────────┤
│☐ │ active                       │ true          │
│☐ │ temperature_threshold        │ 75.5          │
│☐ │ location                     │ Building A    │
└──┴──────────────────────────────┴───────────────┘
```

**After (Maintenance Users):**
```
┌─────────────────────────────────────────────────┐
│ Shared Attributes                               │
├────────────────────────────────┬────────────────┤
│ Key                            │ Value          │
├────────────────────────────────┼────────────────┤
│ active                         │ true           │
│ temperature_threshold          │ 75.5           │
│ location                       │ Building A     │
└────────────────────────────────┴────────────────┘
```

---

# Changes required

## Change 1: Initialize displayedColumns Based on User Role

**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.ts`

### Location: Line 116

**Current Code:**
```typescript
displayedColumns = ['select', 'key', 'value'];
```

**Updated Code:**
```typescript
displayedColumns: string[] = [];
```

**Explanation:**
- Change from hardcoded array to an empty array that will be populated in the constructor
- Remove the hardcoded 'select' column
- Make it explicitly typed as `string[]`

## Change 2: Set displayedColumns in Constructor

**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.ts`

### Location: After Line 213 (in the constructor)

**Current Code (Lines 193-215):**
```typescript
constructor(protected store: Store<AppState>,
            private attributeService: AttributeService,
            private telemetryWsService: TelemetryWebsocketService,
            public translate: TranslateService,
            public dialog: MatDialog,
            private overlay: Overlay,
            private viewContainerRef: ViewContainerRef,
            private dialogService: DialogService,
            private entityService: EntityService,
            private utils: UtilsService,
            private dashboardUtils: DashboardUtilsService,
            private widgetService: WidgetService,
            private zone: NgZone,
            private cd: ChangeDetectorRef,
            private elementRef: ElementRef,
            private fb: FormBuilder) {
  super(store);
  this.dirtyValue = !this.activeValue;
  const sortOrder: SortOrder = { property: 'key', direction: Direction.ASC };
  this.pageLink = new PageLink(10, 0, null, sortOrder);
  this.userRole = getCurrentAuthState(this.store).userDetails.additionalInfo?.role;
  this.dataSource = new AttributeDatasource(this.attributeService, this.telemetryWsService, this.zone, this.translate);
}
```

**Updated Code:**
```typescript
constructor(protected store: Store<AppState>,
            private attributeService: AttributeService,
            private telemetryWsService: TelemetryWebsocketService,
            public translate: TranslateService,
            public dialog: MatDialog,
            private overlay: Overlay,
            private viewContainerRef: ViewContainerRef,
            private dialogService: DialogService,
            private entityService: EntityService,
            private utils: UtilsService,
            private dashboardUtils: DashboardUtilsService,
            private widgetService: WidgetService,
            private zone: NgZone,
            private cd: ChangeDetectorRef,
            private elementRef: ElementRef,
            private fb: FormBuilder) {
  super(store);
  this.dirtyValue = !this.activeValue;
  const sortOrder: SortOrder = { property: 'key', direction: Direction.ASC };
  this.pageLink = new PageLink(10, 0, null, sortOrder);
  this.userRole = getCurrentAuthState(this.store).userDetails.additionalInfo?.role;
  this.dataSource = new AttributeDatasource(this.attributeService, this.telemetryWsService, this.zone, this.translate);

  // Set displayed columns based on user role
  // Hide checkbox selection for Maintenance users
  if (this.userRole === UserRole.MAINTENANCE) {
    this.displayedColumns = ['key', 'value'];
  } else {
    this.displayedColumns = ['select', 'key', 'value'];
  }
}
```

**Explanation:**
- After initializing the user role, conditionally set the `displayedColumns` array
- For Maintenance users: only include 'key' and 'value' columns (no 'select' checkbox column)
- For all other users: include all three columns including 'select'
- This approach ensures the checkbox column is completely removed from the DOM for Maintenance users

## Alternative Approach (Using ngIf in Template)

If you prefer to keep the logic in the template rather than the TypeScript, you could alternatively:

### TypeScript Change:
Add a helper method:
```typescript
shouldShowCheckboxes(): boolean {
  return this.userRole !== UserRole.MAINTENANCE;
}
```

### HTML Change (Line 143):
```html
<ng-container matColumnDef="select" sticky *ngIf="shouldShowCheckboxes()">
  <!-- checkbox content -->
</ng-container>
```

**However, the TypeScript approach (modifying displayedColumns) is recommended because:**
1. It's cleaner and more maintainable
2. It follows the existing pattern in the codebase (see `entities-table.component.ts` line 626)
3. It completely removes the column from the rendered table
4. It prevents any selection-related logic from executing for Maintenance users
5. The selection toolbar (lines 81-107 in HTML) already checks `dataSource.selection.isEmpty()`, so it won't appear if there's no select column

---

## Additional Considerations

### 1. Row Click Behavior
Currently, clicking on a row toggles selection (HTML line 217):
```html
<mat-row ... (click)="dataSource.selection.toggle(attribute)"></mat-row>
```

For Maintenance users, this click won't do anything since there's no checkbox column, but the selection state might still change. Consider adding a conditional check:

```html
<mat-row [ngClass]="{'mat-row-select': displayedColumns.includes('select'),
                     'mat-selected': displayedColumns.includes('select') && dataSource.selection.isSelected(attribute)}"
         *matRowDef="let attribute; columns: displayedColumns;"
         (click)="displayedColumns.includes('select') ? dataSource.selection.toggle(attribute) : null"></mat-row>
```

### 2. Widget Mode Protection
As an extra safety measure, you might want to add a role check in the `enterWidgetMode()` method (line 505):

```typescript
enterWidgetMode() {
  // Prevent Maintenance users from entering widget mode
  if (this.userRole === UserRole.MAINTENANCE) {
    return;
  }

  this.mode = 'widget';
  // ... rest of the method
}
```

### 3. Selection State Protection
For additional safety, you could add guards in the delete methods to ensure Maintenance users can't trigger these actions even if they somehow bypass the UI:

```typescript
deleteAttributes($event: Event) {
  if ($event) {
    $event.stopPropagation();
  }

  // Prevent Maintenance users from deleting attributes
  if (this.userRole === UserRole.MAINTENANCE) {
    return;
  }

  if (this.dataSource.selection.selected.length > 0) {
    // ... rest of the method
  }
}
```

---

## Testing Checklist

After implementing the changes, verify the following:

### For Maintenance User:
- [ ] Login as Maintenance user
- [ ] Navigate to Devices page
- [ ] Click on any device to open device details
- [ ] Open the "Attributes" tab
- [ ] Verify that NO checkboxes appear in the attribute table (neither header nor row checkboxes)
- [ ] Verify that the checkbox column is completely removed (not just hidden)
- [ ] Verify that clicking on attribute rows does NOT trigger any selection
- [ ] Verify that the selection toolbar NEVER appears (even after clicking attributes)
- [ ] Verify that attribute keys and values are still visible and readable
- [ ] Verify that copy buttons for keys and values still work
- [ ] Verify that you can still view different attribute scopes (if applicable)
- [ ] Verify that pagination and sorting still work correctly

### For Admin/Controlytics Admin User:
- [ ] Login as Admin or Controlytics Admin
- [ ] Navigate to Devices page and open device details > Attributes tab
- [ ] Verify that checkboxes ARE visible in the attribute table
- [ ] Verify that clicking the header checkbox selects/deselects all attributes
- [ ] Verify that clicking individual row checkboxes works
- [ ] Verify that the selection toolbar appears when attributes are selected
- [ ] Verify that "Show on Widget" button works
- [ ] Verify that delete functionality works (for admins)
- [ ] Verify that all existing functionality remains intact

### For Operator/Supervisor User:
- [ ] Login as Operator or Supervisor user
- [ ] Navigate to Devices page and open device details > Attributes tab
- [ ] Verify that checkboxes ARE visible in the attribute table
- [ ] Verify that checkbox selection works
- [ ] Verify that "Show on Widget" functionality works
- [ ] Verify that they don't have delete permissions (if applicable)

### Edge Cases:
- [ ] Test with devices that have no attributes
- [ ] Test with devices that have many attributes (pagination)
- [ ] Test switching between different attribute scopes
- [ ] Test with different attribute types (client, shared, server)
- [ ] Test the behavior when switching between devices
- [ ] Verify that the change doesn't affect other entity types (Assets, Customers, etc.)

---

## Summary of Files to Modify

1. **Primary Change:**
   - `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.ts`
     - Line 116: Change displayedColumns initialization
     - After Line 213: Add role-based column configuration in constructor

2. **Optional Safety Enhancements (Recommended):**
   - Same file: Add role checks in `enterWidgetMode()`, `deleteAttributes()`, and `deleteTelemetry()` methods
   - HTML template: Add conditional row click behavior

---

## Implementation Priority

**High Priority (Must Have):**
- Change 1: Initialize displayedColumns as empty array
- Change 2: Set displayedColumns based on role in constructor

**Medium Priority (Recommended):**
- Widget mode protection in `enterWidgetMode()` method
- Delete method protection

**Low Priority (Nice to Have):**
- Conditional row click behavior in template
- Additional UI refinements
