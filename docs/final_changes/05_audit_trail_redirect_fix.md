# Requirement #5: Audit Trail Redirect Fix for Controlytics Admin

## Requirement
When a Controlytics Admin user clicks on the "Audit Trail" menu item, they are being redirected to the Home page instead of viewing the audit trail. This needs to be fixed so that Controlytics Admin users can properly access the audit trail functionality.

**Priority**: DO if time permits

## Current Implementation as per Code

### 1. Menu Configuration
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/core/services/menu.service.ts`

The Controlytics Admin menu (lines 392-705) includes an Audit Log entry within the Security Settings section:

```typescript
{
  id: 'security_settings',
  name: 'security.security',
  type: 'toggle',
  path: '/security-settings',
  icon: 'security',
  pages: [
    {
      id: 'audit_log',
      name: 'audit-log.audit-logs',
      type: 'link',
      path: '/security-settings/auditLogs',  // Line 691
      icon: 'track_changes'
    },
    {
      id: 'security_settings',
      name: 'admin.security-settings',
      type: 'link',
      path: '/security-settings/general',
      icon: 'settings_applications'
    }
  ]
}
```

### 2. Audit Log Routing Configuration
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/audit-log/audit-log-routing.module.ts`

Two route configurations exist:

**Exported Routes** (used in admin-routing.module.ts, line 314):
```typescript
export const auditLogsRoutes: Routes = [
  {
    path: 'auditLogs',
    component: AuditLogTableComponent,
    data: {
      auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER, Authority.SYS_ADMIN],
      title: 'audit-log.audit-logs',
      breadcrumb: {
        label: 'audit-log.audit-logs',
        icon: 'track_changes'
      },
      isPage: true
    }
  }
];
```

**Internal Module Routes**:
```typescript
const routes: Routes = [
  {
    path: 'auditLogs',
    redirectTo: '/security-settings/auditLogs'
  }
];
```

### 3. Admin Routing Configuration
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/admin/admin-routing.module.ts`

The routing includes:
- Line 314: `...auditLogsRoutes` - Spreads the exported route for `/auditLogs` (allows TENANT_ADMIN, CUSTOMER_USER, SYS_ADMIN)
- Lines 316-378: `/security-settings` parent route with **auth: [Authority.SYS_ADMIN]** only
  - This parent route has NO child route for `auditLogs`
  - Children include: `general`, `2fa`, `oauth2` - but NOT `auditLogs`

### 4. Root Cause Analysis

The issue occurs due to a routing misconfiguration:

1. The menu item points to `/security-settings/auditLogs` (line 691 in menu.service.ts)
2. The `/security-settings` parent route (line 316 in admin-routing.module.ts) only allows `Authority.SYS_ADMIN`
3. There is NO child route defined for `/security-settings/auditLogs`
4. When a TENANT_ADMIN (Controlytics Admin) tries to access `/security-settings/auditLogs`:
   - The parent route `/security-settings` blocks access because it requires SYS_ADMIN authority
   - The auth guard (line 318) rejects the request
   - The user is redirected to the Home page (default behavior for unauthorized access)

5. The standalone `/auditLogs` route exists (from auditLogsRoutes) but:
   - The menu doesn't link to it (menu links to `/security-settings/auditLogs`)
   - It has a redirect in audit-log-routing.module.ts that redirects to `/security-settings/auditLogs`, creating a circular problem

## Expectation after the Change

After the fix:
1. Controlytics Admin users should be able to click on "Audit Trail" in the Security Settings menu
2. They should successfully navigate to the audit trail page showing the audit logs table
3. The audit logs should be filtered/displayed according to their tenant and permissions
4. No redirect to Home page should occur
5. The URL should be stable and accessible

## Changes Required

### Option 1: Add auditLogs as a Child Route under security-settings (Recommended)

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/admin/admin-routing.module.ts`

**Change**: Add the auditLogs route as a child of the `security-settings` route, after the `oauth2` route (around line 376):

```typescript
{
  path: 'security-settings',
  data: {
    auth: [Authority.SYS_ADMIN],  // Keep this for parent
    breadcrumb: {
      label: 'security.security',
      icon: 'security'
    }
  },
  children: [
    {
      path: '',
      children: [],
      data: {
        auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
        redirectTo: {
          SYS_ADMIN: '/security-settings/general'
        }
      }
    },
    {
      path: 'general',
      component: SecuritySettingsComponent,
      canDeactivate: [ConfirmOnExitGuard],
      data: {
        auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
        title: 'admin.general',
        breadcrumb: {
          label: 'admin.general',
          icon: 'settings_applications'
        }
      }
    },
    {
      path: '2fa',
      component: TwoFactorAuthSettingsComponent,
      canDeactivate: [ConfirmOnExitGuard],
      data: {
        auth: [Authority.SYS_ADMIN],
        title: 'admin.2fa.2fa',
        breadcrumb: {
          label: 'admin.2fa.2fa',
          icon: 'mdi:two-factor-authentication'
        }
      }
    },
    {
      path: 'oauth2',
      component: OAuth2SettingsComponent,
      canDeactivate: [ConfirmOnExitGuard],
      data: {
        auth: [Authority.SYS_ADMIN],
        title: 'admin.oauth2.oauth2',
        breadcrumb: {
          label: 'admin.oauth2.oauth2',
          icon: 'mdi:shield-account'
        }
      },
      resolve: {
        loginProcessingUrl: OAuth2LoginProcessingUrlResolver
      }
    },
    // ADD THIS NEW CHILD ROUTE
    {
      path: 'auditLogs',
      component: AuditLogTableComponent,
      data: {
        auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER, Authority.SYS_ADMIN],
        title: 'audit-log.audit-logs',
        breadcrumb: {
          label: 'audit-log.audit-logs',
          icon: 'track_changes'
        },
        isPage: true
      }
    }
  ]
}
```

**Additional imports needed** at the top of admin-routing.module.ts:
```typescript
import { AuditLogTableComponent } from '@home/components/audit-log/audit-log-table.component';
```

### Option 2: Change Menu to Point to Standalone /auditLogs Route (Alternative)

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/core/services/menu.service.ts`

**Change**: Update the path in the Controlytics Admin menu (line 691):

```typescript
{
  id: 'audit_log',
  name: 'audit-log.audit-logs',
  type: 'link',
  path: '/auditLogs',  // Changed from '/security-settings/auditLogs'
  icon: 'track_changes'
}
```

However, this approach has a problem: The redirect in audit-log-routing.module.ts would still redirect to `/security-settings/auditLogs`, so you'd also need to:

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/audit-log/audit-log-routing.module.ts`

**Change**: Remove the redirect:

```typescript
const routes: Routes = [
  // Remove or comment out this redirect:
  // {
  //   path: 'auditLogs',
  //   redirectTo: '/security-settings/auditLogs'
  // }
];
```

## Recommendation

**Option 1 is recommended** because:
1. It maintains the existing URL structure (`/security-settings/auditLogs`)
2. It keeps the menu organization consistent (Audit Logs under Security Settings)
3. It only requires changes in one file (admin-routing.module.ts)
4. It properly handles the authorization at the child route level while allowing the parent to restrict other routes
5. The breadcrumb and navigation hierarchy remain logical

**Implementation Steps**:
1. Open `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/admin/admin-routing.module.ts`
2. Add the import for `AuditLogTableComponent` at the top
3. Add the `auditLogs` child route under the `security-settings` children array
4. Test by logging in as Controlytics Admin and clicking on Audit Trail
5. Verify that the audit logs table loads correctly and shows appropriate data

## Testing Checklist

After implementing the fix, verify:
- [ ] Controlytics Admin can navigate to Audit Trail from the Security Settings menu
- [ ] The URL shows `/security-settings/auditLogs`
- [ ] Audit logs table displays correctly with appropriate data for the tenant
- [ ] No redirect to Home page occurs
- [ ] SYS_ADMIN can still access the audit logs
- [ ] CUSTOMER_USER can still access audit logs via their menu
- [ ] Breadcrumb shows correct hierarchy (Security > Audit Logs)
- [ ] Direct URL navigation to `/security-settings/auditLogs` works for authorized users
- [ ] Unauthorized users are properly blocked from accessing the route
