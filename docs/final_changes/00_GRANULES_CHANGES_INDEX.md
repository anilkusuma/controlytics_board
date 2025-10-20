# Granules Final Changes - Implementation Guide

This document provides a consolidated index of all 14 changes required for the Granules implementation as documented in `grnaules_final_changes.pdf`.

## Overview

All changes have been analyzed and documented in separate markdown files. Each file contains:
- **Requirement**: Detailed description of what needs to be changed
- **Current Implementation**: Analysis of existing code behavior
- **Expectation after the change**: Expected behavior after implementation
- **Changes required**: Specific files, line numbers, and code changes needed
- **Reasoning/Options**: For complex changes, multiple approaches with recommendations

## Implementation Progress

**Status**: 9 of 14 changes completed (64%)

- ✅ **Completed**: 9 changes
- ⏳ **Pending**: 5 changes

### Commits
- **Commit 1** (`b140ac5e57`): Changes #1, #5, #7, #11 - User management, UI improvements, and bug fixes
- **Commit 2** (`95278b70e0`): Changes #2, #4, #6, #8, #9 - Role-based UI visibility and permissions improvements

## Change Summary

| # | Change Description | File Reference | Priority | Complexity | Status | Testing Status |
|---|-------------------|----------------|----------|------------|--------|--------|
| 1 | Hide delete action for users (Admin vs Controlytics Admin) | [01_hide_user_delete_action.md](./01_hide_user_delete_action.md) | High | Low | ✅ Done | ✅ Done |
| 2 | JWT Token settings visibility for Controlytics Admin | [02_jwt_token_settings_visibility.md](./02_jwt_token_settings_visibility.md) | High | Medium | ✅ Done | ✅ Done |
| 3 | Admin password change issue (popup bug) | [03_admin_password_change_issue.md](./03_admin_password_change_issue.md) | High | Medium | ⏳ Pending |
| 4 | Attributes display and delete for Controlytics Admin | [04_attributes_display_and_delete.md](./04_attributes_display_and_delete.md) | Medium | Low | ✅ Done | ✅ Done |
| 5 | Audit trail redirect fix for Controlytics Admin | [05_audit_trail_redirect_fix.md](./05_audit_trail_redirect_fix.md) | Low | Low | ✅ Done | ✅ Done |
| 6 | Hide dashboard edit icon for Supervisor/Operator/Maintenance | [06_hide_dashboard_edit_icon.md](./06_hide_dashboard_edit_icon.md) | High | Low | ✅ Done | ✅ Done |
| 7 | Dashboard date format (YYYY/MM/DD to DD/MM/YYYY) | [07_dashboard_date_format.md](./07_dashboard_date_format.md) | Medium | Low | ✅ Done | ✅ Done |
| 8 | Hide device attributes checkbox for Maintenance user | [08_hide_device_attributes_checkbox.md](./08_hide_device_attributes_checkbox.md) | Medium | Low | ✅ Done | ✅ Done |
| 9 | Hide Functional Locations edit option (not working) | [09_hide_functional_locations_edit.md](./09_hide_functional_locations_edit.md) | Medium | Low | ✅ Done | ✅ Done |
| 10 | Auto logout config - password auth & audit trail | [10_auto_logout_password_and_audit.md](./10_auto_logout_password_and_audit.md) | High | Medium | ✅ Done | ✅ Done |
| 11 | Data report title change | [11_data_report_title_change.md](./11_data_report_title_change.md) | Low | Low | ✅ Done | ✅ Done |
| 12 | Password expiry redirect to reset page | [12_password_expiry_redirect.md](./12_password_expiry_redirect.md) | High | High |  ✅ Done | ✅ Done |
| 13 | Admin reset password audit trail logging | [13_admin_reset_password_audit.md](./13_admin_reset_password_audit.md) | High | Medium |  ✅ Done | ✅ Done |
| 14 | Hide Admin UI controls (Add/Refresh/Search/Checkbox) | [14_hide_admin_ui_controls.md](./14_hide_admin_ui_controls.md) | Medium | Low | ✅ Done | ✅ Done |

## Detailed Changes by Category

### 🔐 Security & Authentication (High Priority)

#### 1. Hide Delete Action for Users ✅
- **File**: [01_hide_user_delete_action.md](./01_hide_user_delete_action.md)
- **Status**: ✅ Completed (Commit: `b140ac5e57`)
- **Impact**: Frontend only
- **Files Modified**: `ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`
- **Summary**: Restrict user deletion to Controlytics Admin only, hide from regular Admin users

#### 2. JWT Token Settings Visibility ✅
- **File**: [02_jwt_token_settings_visibility.md](./02_jwt_token_settings_visibility.md)
- **Status**: ✅ Completed (Commit: `95278b70e0`)
- **Impact**: Frontend only
- **Files Modified**:
  - `ui-ngx/src/app/modules/home/pages/admin/security-settings.component.ts`
  - `ui-ngx/src/app/modules/home/pages/admin/security-settings.component.html`
- **Summary**: Show all JWT settings to Controlytics Admin to resolve configuration validation issues

#### 3. Admin Password Change Issue ⏳
- **File**: [03_admin_password_change_issue.md](./03_admin_password_change_issue.md)
- **Status**: ⏳ Pending
- **Impact**: Frontend only
- **Files Modified**: `ui-ngx/src/app/core/auth/auth.service.ts`
- **Summary**: Fix token refresh race condition causing password confirmation popup after password change

#### 10. Auto Logout Password Auth & Audit Trail ⏳
- **File**: [10_auto_logout_password_and_audit.md](./10_auto_logout_password_and_audit.md)
- **Status**: ⏳ Pending
- **Impact**: Frontend + Backend
- **Files Modified**:
  - Frontend: `ui-ngx/src/app/modules/home/pages/admin/security-settings.component.ts`
  - Backend: `application/src/main/java/org/thingsboard/server/controller/AdminController.java`
- **Summary**: Add password authentication and audit logging for JWT settings changes

#### 12. Password Expiry Redirect ⏳
- **File**: [12_password_expiry_redirect.md](./12_password_expiry_redirect.md)
- **Status**: ⏳ Pending
- **Impact**: Frontend only
- **Files Modified**:
  - `ui-ngx/src/app/core/interceptors/global-http-interceptor.ts`
  - `ui-ngx/src/app/core/auth/auth.service.ts`
- **Summary**: Fix critical bugs preventing redirect to password reset page when password expires

#### 13. Admin Reset Password Audit Trail ⏳
- **File**: [13_admin_reset_password_audit.md](./13_admin_reset_password_audit.md)
- **Status**: ⏳ Pending
- **Impact**: Backend only
- **Files Modified**: `application/src/main/java/org/thingsboard/server/controller/UserController.java`
- **Summary**: Add audit logging when admin resets user passwords

### 👁️ UI Visibility & Permissions (High Priority)

#### 6. Hide Dashboard Edit Icon ✅
- **File**: [06_hide_dashboard_edit_icon.md](./06_hide_dashboard_edit_icon.md)
- **Status**: ✅ Completed (Commit: `95278b70e0`)
- **Impact**: Frontend only
- **Files Modified**: `ui-ngx/src/app/modules/home/pages/dashboard/dashboards-table-config.resolver.ts`
- **Summary**: Hide pencil icon for Supervisor, Operator, and Maintenance users

#### 14. Hide Admin UI Controls ⏳
- **File**: [14_hide_admin_ui_controls.md](./14_hide_admin_ui_controls.md)
- **Status**: ⏳ Pending
- **Impact**: Frontend only
- **Files Modified**:
  - `ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`
  - `ui-ngx/src/app/modules/home/components/entity/entities-table.component.html`
- **Summary**: Hide Add, Refresh, Search, and Checkbox controls for Admin role in Users page

### 🎨 Display & Formatting (Medium Priority)

#### 4. Attributes Display and Delete ✅
- **File**: [04_attributes_display_and_delete.md](./04_attributes_display_and_delete.md)
- **Status**: ✅ Completed (Commit: `95278b70e0`)
- **Impact**: Frontend only
- **Files Modified**:
  - `ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.html`
  - `ui-ngx/src/app/shared/pipe/tbJson.pipe.ts` (optional)
- **Summary**: Fix HTML syntax error, enable delete button, improve JSON formatting for Controlytics Admin

#### 7. Dashboard Date Format ✅
- **File**: [07_dashboard_date_format.md](./07_dashboard_date_format.md)
- **Status**: ✅ Completed (Commit: `b140ac5e57`)
- **Impact**: Frontend only
- **Files Modified**: `ui-ngx/src/app/shared/components/time/timewindow.component.ts`
- **Summary**: Change date format from YYYY/MM/DD to DD/MM/YYYY in dashboard time selector

#### 8. Hide Device Attributes Checkbox ✅
- **File**: [08_hide_device_attributes_checkbox.md](./08_hide_device_attributes_checkbox.md)
- **Status**: ✅ Completed (Commit: `95278b70e0`)
- **Impact**: Frontend only
- **Files Modified**: `ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.ts`
- **Summary**: Hide checkbox selection column for Maintenance users in device attributes

#### 9. Hide Functional Locations Edit ✅
- **File**: [09_hide_functional_locations_edit.md](./09_hide_functional_locations_edit.md)
- **Status**: ✅ Completed (Commit: `95278b70e0`)
- **Impact**: Frontend only
- **Files Modified**: `ui-ngx/src/app/modules/home/pages/asset/assets-table-config.resolver.ts`
- **Summary**: Make Functional Locations details panel read-only by always returning true

#### 11. Data Report Title Change ✅
- **File**: [11_data_report_title_change.md](./11_data_report_title_change.md)
- **Status**: ✅ Completed (Commit: `b140ac5e57`)
- **Impact**: Report template only
- **Files Modified**: `common/report-gen/src/main/resources/pdf_templates/granules-device-timeseries-report.html`
- **Summary**: Change report title from "TEMP AND RH LOG REPORT" to "Temperature and RH Data Report"

### 🔧 Bug Fixes & Navigation (Low Priority - Optional)

#### 5. Audit Trail Redirect Fix ✅
- **File**: [05_audit_trail_redirect_fix.md](./05_audit_trail_redirect_fix.md)
- **Status**: ✅ Completed (Commit: `b140ac5e57`)
- **Impact**: Frontend only
- **Files Modified**: `ui-ngx/src/app/modules/home/pages/admin/admin-routing.module.ts`
- **Summary**: Add auditLogs child route under security-settings for Controlytics Admin access
- **Note**: Marked as "DO if time permits" in requirements

## Implementation Recommendations

### Phase 1: Critical Security Fixes (High Priority)
Implement these first as they affect security and user management:
1. ⏳ Change #3: Admin password change issue (bug fix)
2. ⏳ Change #12: Password expiry redirect (critical bug)
3. ✅ Change #1: Hide delete action for users
4. ⏳ Change #10: Auto logout audit trail
5. ⏳ Change #13: Admin reset password audit trail

### Phase 2: UI Permissions (High Priority)
Implement role-based UI visibility:
1. ✅ Change #6: Hide dashboard edit icon
2. ⏳ Change #14: Hide Admin UI controls
3. ✅ Change #2: JWT token settings visibility

### Phase 3: Display & Formatting (Medium Priority)
UI improvements and formatting fixes:
1. ✅ Change #4: Attributes display and delete
2. ✅ Change #7: Dashboard date format
3. ✅ Change #8: Hide device attributes checkbox
4. ✅ Change #9: Hide Functional Locations edit

### Phase 4: Polish & Optional (Low Priority)
Final touches and optional enhancements:
1. ✅ Change #11: Data report title
2. ✅ Change #5: Audit trail redirect (optional)

## Testing Checklist

For each change, verify:
- [ ] Admin (TENANT_ADMIN with UserRole.ADMIN) behavior
- [ ] Controlytics Admin (SYS_ADMIN or UserRole.CONTROLYTICS_ADMIN) behavior
- [ ] Other user roles (Supervisor, Operator, Maintenance, Customer User)
- [ ] Audit trail entries are created (where applicable)
- [ ] No console errors in browser
- [ ] No backend errors in logs
- [ ] UI displays correctly on different screen sizes

## File Modification Summary

### Frontend Files (TypeScript/HTML)
- `ui-ngx/src/app/core/auth/auth.service.ts`
- `ui-ngx/src/app/core/interceptors/global-http-interceptor.ts`
- `ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.html`
- `ui-ngx/src/app/modules/home/components/attribute/attribute-table.component.ts`
- `ui-ngx/src/app/modules/home/components/entity/entities-table.component.html`
- `ui-ngx/src/app/modules/home/pages/admin/admin-routing.module.ts`
- `ui-ngx/src/app/modules/home/pages/admin/security-settings.component.html`
- `ui-ngx/src/app/modules/home/pages/admin/security-settings.component.ts`
- `ui-ngx/src/app/modules/home/pages/asset/assets-table-config.resolver.ts`
- `ui-ngx/src/app/modules/home/pages/dashboard/dashboards-table-config.resolver.ts`
- `ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`
- `ui-ngx/src/app/shared/components/time/timewindow.component.ts`
- `ui-ngx/src/app/shared/pipe/tbJson.pipe.ts` (optional)

### Backend Files (Java)
- `application/src/main/java/org/thingsboard/server/controller/AdminController.java`
- `application/src/main/java/org/thingsboard/server/controller/UserController.java`

### Template Files (HTML)
- `common/report-gen/src/main/resources/pdf_templates/granules-device-timeseries-report.html`

## Notes

- **No database migrations required** - All changes are UI, logic, and audit trail related
- **No breaking changes** - All modifications are additive or restrictive (hiding UI elements)
- **Backward compatible** - Existing functionality remains intact for users who should have access
- **Focus on role-based access control** - Many changes involve distinguishing between Admin and Controlytics Admin roles

## Key Role Distinctions

Throughout this implementation, pay attention to these role distinctions:

| Role | Authority | Access Level |
|------|-----------|-------------|
| **Controlytics Admin** | `SYS_ADMIN` or `UserRole.CONTROLYTICS_ADMIN` | Full access, developer/superadmin level |
| **Admin** | `TENANT_ADMIN` with `UserRole.ADMIN` | Tenant administrator, restricted access |
| **Supervisor** | `CUSTOMER_USER` with `UserRole.SUPERVISOR` | View and limited edit access |
| **Operator** | `CUSTOMER_USER` with `UserRole.OPERATOR` | View-only for most features |
| **Maintenance** | `CUSTOMER_USER` with `UserRole.MAINTENANCE` | Specific maintenance-related access |

## Document Version

- **Created**: 2025-10-19
- **Last Updated**: 2025-10-19
- **Source**: grnaules_final_changes.pdf
- **Total Changes**: 14
- **Completed**: 9 (64%)
- **Pending**: 5 (36%)
- **Status**: In Progress

## Remaining Work

### Priority 1: Critical Bug Fixes (3 changes)
1. **Change #3**: Admin password change issue - Fix token refresh race condition
2. **Change #12**: Password expiry redirect - Fix critical redirect bug
3. **Change #10**: Auto logout audit trail - Add password auth and logging

### Priority 2: Security Enhancement (1 change)
4. **Change #13**: Admin reset password audit trail - Add audit logging for admin actions

### Priority 3: UI Enhancement (1 change)
5. **Change #14**: Hide Admin UI controls - Complete role-based access control

---

For detailed implementation instructions for each change, please refer to the individual markdown files linked in the table above.
