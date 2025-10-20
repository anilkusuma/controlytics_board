# Requirement #6: Remove Pencil Icon in Dashboard List for Supervisor, Operator and Maintenance

## Requirement

Remove pencil (edit) icon in Dashboard list for Supervisor, Operator and Maintenance users. Show only Dashboard Names for these roles.

**Current Behavior:**
- Pencil icon is visible for Operator and Supervisor
- Pencil icon is not visible for Maintenance
- It should not be visible for Supervisor, Operator and Maintenance

**Expected Behavior:**
- Hide the pencil/edit icon for all three roles: Supervisor, Operator, and Maintenance
- Only show dashboard names (read-only access)
- Edit icon should only be visible for Admin and Controlytics Admin roles

## Current Implementation as per Code

### User Roles Definition
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/shared/models/user.model.ts`

```typescript
export enum UserRole {
  ADMIN = 'admin',
  OPERATOR = 'operator',
  MAINTENANCE = 'maintenance',
  SUPERVISOR = 'supervisor',
  CONTROLYTICS_ADMIN = 'controlytics_admin',
}
```

### Dashboard Table Configuration
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/dashboard/dashboards-table-config.resolver.ts`

**Lines 286-295:** Current implementation that configures the edit icon
```typescript
if (this.userRole !== UserRole.MAINTENANCE) {
  actions.push(
    {
      name: this.translate.instant('dashboard.dashboard-details'),
      icon: 'edit',
      isEnabled: () => true,
      onAction: ($event, entity) => this.config.toggleEntityDetails($event, entity)
    }
  );
}
```

**Current Logic:**
- The edit icon (pencil) is shown for ALL users EXCEPT Maintenance
- This means: Admin, Controlytics Admin, Supervisor, and Operator can see the edit icon
- Only Maintenance users cannot see it

**Related Configuration (Lines 178-180):**
```typescript
this.config.addEnabled = !(this.config.componentsData.dashboardScope === 'customer_user' ||
  this.config.componentsData.dashboardScope === 'edge_customer_user' || this.userRole === UserRole.MAINTENANCE);
this.config.entitiesDeleteEnabled = this.config.componentsData.dashboardScope === 'tenant' && this.userRole !== UserRole.MAINTENANCE;
this.config.deleteEnabled = () => this.config.componentsData.dashboardScope === 'tenant' && this.userRole !== UserRole.MAINTENANCE;
```

### Dashboard Table UI Rendering
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/entity/entities-table.component.html`

**Lines 217-256:** Actions column rendering
```html
<ng-container matColumnDef="actions" stickyEnd>
  <mat-header-cell *matHeaderCellDef>
    {{ entitiesTableConfig.actionsColumnTitle ? (entitiesTableConfig.actionsColumnTitle | translate) : '' }}
  </mat-header-cell>
  <mat-cell *matCellDef="let entity">
    <div fxLayout="row" fxLayoutAlign="end">
      <button mat-icon-button
              [disabled]="(isLoading$ | async) || !actionDescriptor.isEnabled(entity)"
              *ngFor="let actionDescriptor of cellActionDescriptors"
              matTooltip="{{ actionDescriptor.nameFunction ? actionDescriptor.nameFunction(entity) : actionDescriptor.name }}"
              (click)="actionDescriptor.onAction($event, entity)">
        <tb-icon>
          {{actionDescriptor.iconFunction ?  actionDescriptor.iconFunction(entity) : actionDescriptor.icon}}
        </tb-icon>
      </button>
    </div>
  </mat-cell>
</ng-container>
```

The `cellActionDescriptors` array contains all the action buttons (including the edit icon) that are displayed for each dashboard row.

## Expectation after the Change

### Updated Role-Based Access Control

**Admin and Controlytics Admin:**
- Can see and click the pencil/edit icon
- Full edit access to dashboards
- Can modify dashboard details, configuration, and settings

**Supervisor:**
- Cannot see the pencil/edit icon
- Read-only access to dashboards
- Can only view dashboard names and open dashboards

**Operator:**
- Cannot see the pencil/edit icon
- Read-only access to dashboards
- Can only view dashboard names and open dashboards

**Maintenance:**
- Cannot see the pencil/edit icon (already implemented)
- Read-only access to dashboards
- Can only view dashboard names and open dashboards

## Changes Required

### File to Modify
`/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/dashboard/dashboards-table-config.resolver.ts`

### Change 1: Update the Edit Icon Condition (Lines 286-295)

**Current Code:**
```typescript
if (this.userRole !== UserRole.MAINTENANCE) {
  actions.push(
    {
      name: this.translate.instant('dashboard.dashboard-details'),
      icon: 'edit',
      isEnabled: () => true,
      onAction: ($event, entity) => this.config.toggleEntityDetails($event, entity)
    }
  );
}
```

**Updated Code:**
```typescript
// Only show edit icon for Admin and Controlytics Admin
// Hide for Supervisor, Operator, and Maintenance
if (this.userRole !== UserRole.MAINTENANCE &&
    this.userRole !== UserRole.SUPERVISOR &&
    this.userRole !== UserRole.OPERATOR) {
  actions.push(
    {
      name: this.translate.instant('dashboard.dashboard-details'),
      icon: 'edit',
      isEnabled: () => true,
      onAction: ($event, entity) => this.config.toggleEntityDetails($event, entity)
    }
  );
}
```

**Alternative (more maintainable) approach using allowlist:**
```typescript
// Only allow Admin and Controlytics Admin to see edit icon
const allowedRoles = [UserRole.ADMIN, UserRole.CONTROLYTICS_ADMIN];
if (allowedRoles.includes(this.userRole)) {
  actions.push(
    {
      name: this.translate.instant('dashboard.dashboard-details'),
      icon: 'edit',
      isEnabled: () => true,
      onAction: ($event, entity) => this.config.toggleEntityDetails($event, entity)
    }
  );
}
```

### Testing Requirements

After implementing the changes, verify the following:

1. **Admin User:**
   - Should see the pencil/edit icon in dashboard list
   - Clicking the icon should open dashboard details panel
   - Can modify dashboard settings

2. **Controlytics Admin User:**
   - Should see the pencil/edit icon in dashboard list
   - Clicking the icon should open dashboard details panel
   - Can modify dashboard settings

3. **Supervisor User:**
   - Should NOT see the pencil/edit icon in dashboard list
   - Dashboard row should only show dashboard name
   - Can click on dashboard name to view/open dashboard

4. **Operator User:**
   - Should NOT see the pencil/edit icon in dashboard list
   - Dashboard row should only show dashboard name
   - Can click on dashboard name to view/open dashboard

5. **Maintenance User:**
   - Should NOT see the pencil/edit icon in dashboard list (already working)
   - Dashboard row should only show dashboard name
   - Can click on dashboard name to view/open dashboard

### UI Impact

**Before Change:**
```
Dashboard List Table
------------------------------------------------------------
Dashboard Name          | Actions Column
------------------------------------------------------------
Production Dashboard    | [pencil icon] (visible to Operator, Supervisor)
Test Dashboard          | [pencil icon] (visible to Operator, Supervisor)
```

**After Change:**
```
Dashboard List Table
------------------------------------------------------------
Dashboard Name          | Actions Column
------------------------------------------------------------
Production Dashboard    | (empty - no pencil icon for Operator, Supervisor, Maintenance)
Test Dashboard          | (empty - no pencil icon for Operator, Supervisor, Maintenance)
```

### Additional Notes

1. The change only affects the `configureCellActions()` method which controls row-level action buttons
2. Other dashboard permissions (add, delete, export, etc.) are already properly restricted based on roles
3. The dashboard detail view access is controlled separately and may need additional restrictions
4. Consider similar restrictions in other components if they also show edit icons (e.g., dashboard-form.component)
5. No backend changes are required - this is purely a UI restriction
6. Users can still open/view dashboards by clicking on the dashboard name (controlled by `handleRowClick`)
