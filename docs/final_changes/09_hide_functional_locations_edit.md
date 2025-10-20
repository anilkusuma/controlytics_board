# Requirement

**Requirement #9: Hide Edit Option in Functional Locations Screen**

In the Functional Locations (Assets) screen, the edit option (pencil icon) is not functioning properly. Since the edit functionality is not needed, the edit button should be hidden from the details panel.

## Context
- Screen: Functional Locations (Assets listing and details)
- Issue: Edit/pencil icon button is not working
- Solution: Hide the edit option completely since it's not required

---

# Current Implementation as per Code

## 1. Entity Table Configuration
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/asset/assets-table-config.resolver.ts`

- Line 107-108: The `detailsReadonly` function is configured to return `true` only for `customer_user` and `edge_customer_user` scopes:
```typescript
this.config.detailsReadonly = () => (this.config.componentsData.assetScope === 'customer_user' ||
  this.config.componentsData.assetScope === 'edge_customer_user');
```

- For `tenant`, `customer`, and `edge` scopes, the `detailsReadonly` returns `false`, which means the edit button is shown.

## 2. Entity Details Panel Component
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/entity/entity-details-panel.component.html`

- Line 21: The `isReadOnly` property is passed from the `entitiesTableConfig.detailsReadonly(entity)`:
```html
[isReadOnly]="entitiesTableConfig.detailsReadonly(entity)"
```

## 3. Details Panel Component (Main Component with Edit Button)
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/components/details-panel.component.html`

- Line 47: The entire section with edit buttons is conditionally shown based on `!isReadOnly`:
```html
<section *ngIf="!isReadOnly" fxLayout="row" class="layout-wrap tb-header-buttons">
```

- Lines 48-56: "Apply changes" button (checkmark icon) shown when in edit mode
- Lines 57-64: Toggle edit mode button (pencil/close icon):
```html
<button [disabled]="(isLoading$ | async) || (isAlwaysEdit && !theForm?.dirty)"
        mat-fab
        matTooltip="{{ (isAlwaysEdit ? 'action.decline-changes' : (isEdit ? 'action.decline-changes' : 'details.toggle-edit-mode')) | translate }}"
        matTooltipPosition="above"
        color="accent" class="tb-btn-header mat-fab-bottom-right"
        (click)="onToggleDetailsEditMode()">
  <mat-icon class="material-icons">{{isEdit ? 'close' : 'edit'}}</mat-icon>
</button>
```

The pencil icon (edit button) shows the 'edit' icon when not in edit mode, and 'close' icon when in edit mode.

## 4. Asset Component
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/asset/asset.component.html`

The asset details form and action buttons are present, but the edit functionality is controlled by the details panel's edit mode.

---

# Expectation after the change

After implementing the changes:

1. The edit button (pencil icon) in the Functional Locations details panel should be completely hidden for all users
2. Users should see a read-only view of Functional Location details
3. The "Apply changes" button (checkmark) should also be hidden
4. The form fields should remain disabled/read-only
5. This change should apply to all asset scopes: `tenant`, `customer`, `customer_user`, `edge`, and `edge_customer_user`

---

# Changes required

## Change 1: Update Assets Table Config Resolver
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/asset/assets-table-config.resolver.ts`

**Location:** Lines 107-108

**Current Code:**
```typescript
this.config.detailsReadonly = () => (this.config.componentsData.assetScope === 'customer_user' ||
  this.config.componentsData.assetScope === 'edge_customer_user');
```

**Updated Code:**
```typescript
this.config.detailsReadonly = () => true;  // Always set to read-only to hide edit option
```

**Explanation:**
- Change the `detailsReadonly` function to always return `true` instead of checking the scope
- This will make the details panel always read-only for all asset scopes
- The `isReadOnly` flag in the details panel component will always be `true`, which will hide the edit buttons section (pencil icon and apply button)

## Alternative Change (More Explicit):
If you want to be more explicit about hiding only for Functional Locations:

```typescript
this.config.detailsReadonly = () => {
  // Hide edit option for Functional Locations as it's not working
  return true;
};
```

---

## Testing Checklist

After making the changes, verify:

1. [ ] Navigate to Functional Locations screen
2. [ ] Click on any Functional Location to open details panel
3. [ ] Verify that the pencil (edit) button is NOT visible in the top right corner
4. [ ] Verify that the checkmark (apply changes) button is NOT visible
5. [ ] Verify that form fields are displayed in read-only mode
6. [ ] Test with different user scopes: tenant, customer, customer_user
7. [ ] Verify that the close button (X) is still visible and functional
8. [ ] Verify that other action buttons (Delete, Copy ID, etc.) still work for CONTROLYTICS_ADMIN users

---

# Implementation Status

## Completed Changes

**Date:** 2025-10-19

### Change 1: Updated Assets Table Config Resolver
**File:** `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/asset/assets-table-config.resolver.ts`

**Location:** Line 107

**Change Summary:**
- Modified the `detailsReadonly` function to always return `true`
- Previously, it only returned `true` for `customer_user` and `edge_customer_user` scopes
- Now it returns `true` for all scopes, effectively hiding the edit (pencil) icon for all users

**Code Changed:**
```typescript
// Before:
this.config.detailsReadonly = () => (this.config.componentsData.assetScope === 'customer_user' ||
  this.config.componentsData.assetScope === 'edge_customer_user');

// After:
this.config.detailsReadonly = () => true;  // Always set to read-only to hide edit option
```

**Impact:**
- The edit button (pencil icon) will be completely hidden in the Functional Locations details panel
- The "Apply changes" button (checkmark icon) will also be hidden
- All form fields will be displayed in read-only mode
- This applies to all user scopes: tenant, customer, customer_user, edge, and edge_customer_user
- The close button (X) and other action buttons remain functional

**Next Steps:**
- Frontend build required: `cd ui-ngx && yarn build:prod`
- Testing required as per the checklist above
