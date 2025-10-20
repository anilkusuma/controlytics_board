# Requirement

Delete Action should be hidden for users group and individual users for Admin users. Delete option should be available only for Controlytics admin (developer/SYS_ADMIN). In user details section, remove delete user option for Admin users.

**Context:**
- Admin users (with role UserRole.ADMIN) should NOT see delete options for users
- Only Controlytics Admin (with role UserRole.CONTROLYTICS_ADMIN or Authority.SYS_ADMIN) should see delete options
- This applies to:
  1. Users Group list (Tenant Admins with role ADMIN or MAINTENANCE)
  2. Individual Users list (Customer Users)
  3. User details section delete button

---

# Current Implementation as per Code

## 1. Authority vs UserRole Structure

The application uses two separate concepts:

**Authority Enum** (`/ui-ngx/src/app/shared/models/authority.enum.ts`):
- `SYS_ADMIN` - System Administrator (Controlytics Admin at platform level)
- `TENANT_ADMIN` - Tenant Administrator
- `CUSTOMER_USER` - Customer User
- `REFRESH_TOKEN`, `ANONYMOUS`, `PRE_VERIFICATION_TOKEN`

**UserRole Enum** (`/ui-ngx/src/app/shared/models/user.model.ts`):
- `ADMIN` - Admin role (application-level admin)
- `OPERATOR` - Operator role
- `MAINTENANCE` - Maintenance Personnel role
- `SUPERVISOR` - Supervisor role
- `CONTROLYTICS_ADMIN` - Controlytics Admin role (special developer role)

**Key Understanding:**
- A user with `Authority.TENANT_ADMIN` can have `UserRole.ADMIN`, `UserRole.MAINTENANCE`, or `UserRole.CONTROLYTICS_ADMIN`
- A user with `Authority.SYS_ADMIN` is typically a platform-level Controlytics Admin
- The requirement refers to `UserRole.ADMIN` (not `Authority.TENANT_ADMIN`) when saying "Admin users"

## 2. User Lists Implementation

### Users Table Configuration (`/ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`)

**Line 99: `deleteEnabled` configuration**
```typescript
this.config.deleteEnabled = user => user && user.id && user.id.id !== this.authUser.id.id;
```

**Current behavior:**
- Delete is enabled for all users EXCEPT the currently logged-in user (prevents self-deletion)
- No role-based filtering exists

**Line 208-225: Delete button addition in entities table**
(`/ui-ngx/src/app/modules/home/components/entity/entities-table.component.ts`)
```typescript
if (this.entitiesTableConfig.entitiesDeleteEnabled) {
  this.cellActionDescriptors.push({
    name: this.translate.instant('action.delete'),
    icon: 'delete',
    isEnabled: entity => this.entitiesTableConfig.deleteEnabled(entity),
    onAction: ($event, entity) => this.deleteEntity($event, entity)
  });
}
```

**Current behavior:**
- Delete icon appears in the actions column for each row
- Visibility controlled by `deleteEnabled()` function
- Shows for all users who pass the `deleteEnabled` check

### User Fetching Logic

**Lines 118-143: User fetching based on role**
```typescript
if (this.authUser.additionalInfo.role === UserRole.ADMIN) {
  // Fetches both Tenant Admins (with role ADMIN or MAINTENANCE) and Customer Users
  this.config.entitiesFetchFunction = pageLink => forkJoin([
    this.userService.getTenantAdmins(this.tenantId, pageLink),
    this.userService.getCustomerUsers(this.customerId, pageLink)
  ]).pipe(
    map(([tenantAdmins, customerUsers]) => ({
      data: [...tenantAdmins.data.filter(user => user.additionalInfo?.role === UserRole.ADMIN
        || user.additionalInfo?.role === UserRole.MAINTENANCE), ...customerUsers.data],
      // ...
    }))
  );
}
```

**Users displayed:**
- **Users Group**: Tenant Admins with role ADMIN or MAINTENANCE
- **Individual Users**: Customer Users (CUSTOMER_USER authority)

## 3. User Details Section

### User Component (`/ui-ngx/src/app/modules/home/pages/user/user.component.ts`)

**Line 64-70: `hideDelete()` method**
```typescript
hideDelete() {
  if (this.entitiesTableConfig) {
    return !this.entitiesTableConfig.deleteEnabled(this.entity);
  } else {
    return false;
  }
}
```

**Line 57-60: Delete button in template**
(`/ui-ngx/src/app/modules/home/pages/user/user.component.html`)
```html
<button mat-raised-button color="primary"
        [disabled]="(isLoading$ | async)"
        (click)="onEntityAction($event, 'delete')"
        [fxShow]="!hideDelete() && !isEdit">
  {{'user.delete' | translate }}
</button>
```

**Current behavior:**
- Delete button is shown in the user details panel
- Visibility controlled by `hideDelete()` method which checks `deleteEnabled()`
- Uses the same logic as the table delete icons (no role-based filtering)

## 4. Re-authentication Logic

**Lines 245-281: Delete user with re-authentication**
```typescript
deleteUser(id: any): Observable<any> {
  if (this.authUser.additionalInfo?.role === UserRole.ADMIN) {
    // Shows re-authentication dialog for ADMIN role
    return new Observable(observer => {
      this.dialogService.relogin({...}).subscribe((result) => {
        if (result && result.reloginStatus) {
          this.userService.deleteUser(id.id).subscribe(...);
        }
      });
    });
  } else {
    // Skip re-authentication for Controlytics Admin or other roles
    return this.userService.deleteUser(id.id);
  }
}
```

**Current behavior:**
- ADMIN users can delete after re-authentication
- CONTROLYTICS_ADMIN users can delete without re-authentication
- Both roles can currently delete users

---

# Expectation after the Change

## Delete Action Visibility Matrix

| User Role | Users Group Delete | Individual Users Delete | User Details Delete |
|-----------|-------------------|------------------------|---------------------|
| UserRole.ADMIN | ❌ Hidden | ❌ Hidden | ❌ Hidden |
| UserRole.MAINTENANCE | ❌ Hidden | ❌ Hidden | ❌ Hidden |
| UserRole.OPERATOR | ❌ Hidden | ❌ Hidden | ❌ Hidden |
| UserRole.SUPERVISOR | ❌ Hidden | ❌ Hidden | ❌ Hidden |
| UserRole.CONTROLYTICS_ADMIN | ✅ Visible | ✅ Visible | ✅ Visible |
| Authority.SYS_ADMIN | ✅ Visible | ✅ Visible | ✅ Visible |

## Expected Behavior

1. **Users Group List (Tenant Admins table)**
   - Delete icon in actions column should be hidden for UserRole.ADMIN
   - Delete icon should be visible only for UserRole.CONTROLYTICS_ADMIN or Authority.SYS_ADMIN

2. **Individual Users List (Customer Users table)**
   - Delete icon in actions column should be hidden for UserRole.ADMIN
   - Delete icon should be visible only for UserRole.CONTROLYTICS_ADMIN or Authority.SYS_ADMIN

3. **User Details Section**
   - Delete button should be hidden for UserRole.ADMIN
   - Delete button should be visible only for UserRole.CONTROLYTICS_ADMIN or Authority.SYS_ADMIN

4. **Group Delete Action**
   - When multiple users are selected, the group delete action should follow the same rules
   - Should be hidden for UserRole.ADMIN
   - Should be visible only for UserRole.CONTROLYTICS_ADMIN or Authority.SYS_ADMIN

---

# Changes Required

## File 1: `/ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`

### Change 1.1: Update `deleteEnabled` function (Line 99)

**Current Code:**
```typescript
this.config.deleteEnabled = user => user && user.id && user.id.id !== this.authUser.id.id;
```

**New Code:**
```typescript
this.config.deleteEnabled = user => {
  // Prevent self-deletion
  if (!user || !user.id || user.id.id === this.authUser.id.id) {
    return false;
  }

  // Only CONTROLYTICS_ADMIN or SYS_ADMIN can delete users
  const currentUserRole = this.authUser.additionalInfo?.role;
  const currentAuthority = this.authUser.authority;

  return currentUserRole === UserRole.CONTROLYTICS_ADMIN ||
         currentAuthority === Authority.SYS_ADMIN;
};
```

**Explanation:**
- First check prevents self-deletion (existing behavior)
- Second check ensures only CONTROLYTICS_ADMIN or SYS_ADMIN can see delete buttons
- This will automatically hide delete icons in both Users Group and Individual Users lists
- Will also hide the delete button in user details section

### Change 1.2: Simplify `deleteUser` function (Line 245-281)

**Current Code:**
```typescript
deleteUser(id: any): Observable<any> {
  if (this.authUser.additionalInfo?.role === UserRole.ADMIN) {
    // Re-authentication logic for ADMIN
    return new Observable(observer => { ... });
  } else {
    // Skip re-authentication for Controlytics Admin or other roles
    return this.userService.deleteUser(id.id);
  }
}
```

**New Code:**
```typescript
deleteUser(id: any): Observable<any> {
  // Since delete is only available to CONTROLYTICS_ADMIN or SYS_ADMIN,
  // no re-authentication is needed
  return this.userService.deleteUser(id.id);
}
```

**Explanation:**
- Since ADMIN users can no longer access delete functionality, re-authentication logic is no longer needed
- Simplifies the code by removing conditional re-authentication
- CONTROLYTICS_ADMIN and SYS_ADMIN can delete directly without re-authentication

**Alternative (if you want to keep re-auth for audit purposes):**
```typescript
deleteUser(id: any): Observable<any> {
  // Only CONTROLYTICS_ADMIN or SYS_ADMIN can reach this point
  // due to deleteEnabled check
  const currentUserRole = this.authUser.additionalInfo?.role;

  if (currentUserRole === UserRole.CONTROLYTICS_ADMIN) {
    // Optional: Add re-authentication for CONTROLYTICS_ADMIN if needed
    return new Observable(observer => {
      this.dialogService.relogin({
        remarksRequired: false,
        intervalRequired: false,
        timeRangeRequired: false,
        userNameInputRequired: false
      } as ReLoginDialogComponentData).subscribe((result) => {
        if (result && result.reloginStatus) {
          this.userService.deleteUser(id.id).subscribe(
            response => {
              observer.next(response);
              observer.complete();
            },
            error => observer.error(error)
          );
        } else {
          observer.error(new Error('Re-authentication failed'));
        }
      }, error => observer.error(error));
    });
  } else {
    // SYS_ADMIN can delete without re-authentication
    return this.userService.deleteUser(id.id);
  }
}
```

## File 2: No changes needed to other files

The following files do NOT need changes:

1. **`/ui-ngx/src/app/modules/home/pages/user/user.component.ts`**
   - The `hideDelete()` method already uses `entitiesTableConfig.deleteEnabled()`
   - It will automatically use the updated logic

2. **`/ui-ngx/src/app/modules/home/pages/user/user.component.html`**
   - The delete button already uses `[fxShow]="!hideDelete() && !isEdit"`
   - It will automatically hide based on the updated logic

3. **`/ui-ngx/src/app/modules/home/components/entity/entities-table.component.ts`**
   - The delete icon rendering already uses `isEnabled: entity => this.entitiesTableConfig.deleteEnabled(entity)`
   - It will automatically hide based on the updated logic

## Testing Checklist

After implementing the changes, verify:

### As UserRole.ADMIN:
- [ ] Users Group list: Delete icon is NOT visible in actions column
- [ ] Individual Users list: Delete icon is NOT visible in actions column
- [ ] User details page: Delete button is NOT visible
- [ ] Multi-select: Group delete button is NOT visible when users are selected

### As UserRole.CONTROLYTICS_ADMIN:
- [ ] Users Group list: Delete icon IS visible in actions column
- [ ] Individual Users list: Delete icon IS visible in actions column
- [ ] User details page: Delete button IS visible
- [ ] Multi-select: Group delete button IS visible when users are selected
- [ ] Can successfully delete a user

### As Authority.SYS_ADMIN:
- [ ] Users Group list: Delete icon IS visible in actions column
- [ ] Individual Users list: Delete icon IS visible in actions column
- [ ] User details page: Delete button IS visible
- [ ] Multi-select: Group delete button IS visible when users are selected
- [ ] Can successfully delete a user

### Edge Cases:
- [ ] Cannot delete own user account (all roles)
- [ ] Delete action requires appropriate permissions on backend

---

# Summary

**Files to modify:** 1 file
- `/ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`

**Changes:**
1. Update `deleteEnabled` function to check user role (CONTROLYTICS_ADMIN or SYS_ADMIN)
2. Simplify or update `deleteUser` function to remove unnecessary re-authentication for ADMIN role

**Impact:**
- Delete icons in Users Group list will be hidden for ADMIN users
- Delete icons in Individual Users list will be hidden for ADMIN users
- Delete button in User details section will be hidden for ADMIN users
- Group delete action will be hidden for ADMIN users
- Only CONTROLYTICS_ADMIN or SYS_ADMIN users will see delete functionality

**Risk Level:** Low
- Changes are localized to one configuration file
- Uses existing role checking mechanism
- Follows existing patterns in the codebase
