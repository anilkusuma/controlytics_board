# ADMIN Role Users List & Details Page Analysis

## Executive Summary

The system implements a sophisticated role-based access control mechanism that distinguishes between:
1. **ADMIN Role** (UserRole.ADMIN) - Tenant-level administrator 
2. **Controlytics Admin** (UserRole.CONTROLYTICS_ADMIN or Authority.SYS_ADMIN) - Platform-level admin

When ADMIN users view the users list and user details pages, there are several key hacks and workarounds implemented to allow cross-customer access while maintaining security boundaries.

---

## Key Architecture Components

### 1. Role Distinction

**Frontend Models** (`ui-ngx/src/app/shared/models/user.model.ts`):
- Authority Enum: Controls platform-level permission (TENANT_ADMIN, CUSTOMER_USER, SYS_ADMIN)
- UserRole Enum: Controls application-level permission (ADMIN, OPERATOR, MAINTENANCE, CONTROLYTICS_ADMIN)

Key relationship:
- A user with Authority.TENANT_ADMIN can have UserRole.ADMIN, MAINTENANCE, or CONTROLYTICS_ADMIN
- A user with Authority.SYS_ADMIN is a platform-level superuser

### 2. Permission Checking Architecture

**Backend** (`DefaultAccessControlService.java`):
- Authority-based permissions are checked first
- No UserRole-based permission checking at backend level
- Relies on frontend to enforce UserRole-based restrictions

---

## ADMIN Role User List Implementation

### Frontend User List Fetching (Lines 134-149)

**File**: `ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`

```typescript
if (this.authUser.additionalInfo.role === UserRole.ADMIN) {
  this.tenantId = this.authUser.tenantId.id;
  this.customerId = routeParams.customerId;
  this.config.entitiesFetchFunction = pageLink => forkJoin([
    this.userService.getTenantAdmins(this.tenantId, pageLink),
    this.userService.getCustomerUsers(this.customerId, pageLink)
  ]).pipe(
    map(([tenantAdmins, customerUsers]) => ({
      data: [...tenantAdmins.data.filter(user => user.additionalInfo?.role === UserRole.ADMIN
        || user.additionalInfo?.role === UserRole.MAINTENANCE), ...customerUsers.data],
      totalPages: Math.max(tenantAdmins.totalPages, customerUsers.totalPages),
      totalElements: tenantAdmins.data.filter(user => user.additionalInfo?.role === UserRole.ADMIN
        || user.additionalInfo?.role === UserRole.MAINTENANCE).length + customerUsers.totalElements,
      hasNext: tenantAdmins.hasNext || customerUsers.hasNext
    }))
  );
}
```

### Key Hack #1: Multi-API Fusion for Cross-Boundary Access

**The Hack**:
1. Calls TWO SEPARATE APIs:
   - `getTenantAdmins()` - Gets all tenant-level admins (Authority.TENANT_ADMIN)
   - `getCustomerUsers()` - Gets customer-specific users (Authority.CUSTOMER_USER)

2. **Merges them in frontend** with custom filtering:
   - From tenant admins: Only includes ADMIN or MAINTENANCE roles
   - From customer users: Includes ALL users
   - Combines pagination across both datasets

**Why This Is a Hack**:
- ADMIN users get view access to users from a specific customer they're managing
- The system doesn't enforce backend API restrictions - it's purely frontend-driven
- The backend APIs (`getTenantAdmins`, `getCustomerUsers`) don't have special ADMIN role handling
- The merging logic is custom business logic, not based on standard permissions

**Potential Issue**:
- If ADMIN customerId parameter is manipulated or if backend API security is weak, ADMIN could potentially access any customer's users

---

## Routing Pattern for ADMIN Users

### File: `ui-ngx/src/app/modules/home/pages/customer/customer-routing.module.ts`

All user-related routes are restricted to `Authority.TENANT_ADMIN`:

```typescript
{
  path: ':customerId/users',
  children: [
    {
      path: '',
      data: {
        auth: [Authority.TENANT_ADMIN],  // Only TENANT_ADMIN allowed
        title: 'user.customer-users'
      },
      resolve: {
        entitiesTableConfig: UsersTableConfigResolver
      }
    },
    {
      path: ':entityId',
      data: {
        auth: [Authority.TENANT_ADMIN],  // Only TENANT_ADMIN allowed
        title: 'user.customer-users'
      }
    }
  ]
}
```

**How ADMIN Bypasses This**:
1. ADMIN users have Authority.TENANT_ADMIN (not Authority.CUSTOMER_USER)
2. The router guard checks for `Authority.TENANT_ADMIN`
3. ADMIN passes this check
4. Once in the resolver, the customerId parameter in the route is used to fetch user data

---

## Critical Parameter Handling

### Hack #2: CustomerId Parameter Passthrough

**File**: `users-table-config.resolver.ts` Lines 134-136:

```typescript
if (this.authUser.additionalInfo.role === UserRole.ADMIN) {
  this.tenantId = this.authUser.tenantId.id;
  this.customerId = routeParams.customerId;  // Directly uses route parameter
  // ...
}
```

**The Hack**:
- ADMIN users directly access the customerId from route parameters
- No validation that ADMIN owns/has permission for this customerId
- The system trusts the frontend routing layer completely
- There's NO backend validation that ADMIN can access this customer

**Trust Chain**:
1. Frontend router allows ADMIN to navigate to `/customers/:customerId/users`
2. Resolver trusts the :customerId parameter
3. Calls `getCustomerUsers(this.customerId, pageLink)` without permission check
4. Backend API assumes caller has permission if they reach that endpoint

---

## User Save/Update Flow for ADMIN (Hack #3)

### Re-authentication Pattern

**File**: `users-table-config.resolver.ts` Lines 197-233:

```typescript
saveUser(user: User): Observable<User> {
  if (this.authUser.additionalInfo?.role === UserRole.ADMIN) {
    // Return an Observable that handles re-authentication
    return new Observable(observer => {
      this.dialogService.relogin({
        remarksRequired: false,
        intervalRequired: false,
        timeRangeRequired: false,
        userNameInputRequired: false
      } as ReLoginDialogComponentData).subscribe(
        (result) => {
          if (result && result.reloginStatus) {
            // Proceed with user save after successful re-authentication
            this.performSaveUser(user).subscribe(
              response => { observer.next(response); observer.complete(); },
              error => { observer.error(error); }
            );
          }
        }
      );
    });
  } else {
    // Skip re-authentication for Controlytics Admin
    return this.performSaveUser(user);
  }
}
```

**The Hack**:
1. **ADMIN users must re-authenticate** before modifying ANY user data (even customer users)
2. This is NOT a backend requirement - purely frontend enforcement
3. Re-authentication dialog appears, but doesn't validate permissions
4. After re-authentication, calls `performSaveUser()` which ALSO includes hack

### Hack #4: Authority Override in performSaveUser

```typescript
private performSaveUser(user: User): Observable<User> {
  user.tenantId = new TenantId(this.tenantId);
  user.customerId = new CustomerId(this.customerId);
  if (user.additionalInfo.role === UserRole.ADMIN
    || user.additionalInfo.role === UserRole.MAINTENANCE) {
    user.authority = Authority.TENANT_ADMIN;  // OVERRIDE authority
    user.customerId = undefined;              // CLEAR customer ID
  } else {
    user.authority = this.authority;
  }
  return this.userService.saveUser(user);
}
```

**Critical Hack**:
1. **Modifies user.authority** before sending to backend
2. If saving an ADMIN or MAINTENANCE user, sets `authority = TENANT_ADMIN` and clears `customerId`
3. This is business logic rewriting, not permission checking
4. ADMIN role is elevated to TENANT_ADMIN at save time

**Potential Issue**:
- If frontend JS is compromised, attacker could modify authority of any user
- Backend must also validate this (verify it does in UserController.saveUser)

---

## User View/Open Operation

### Hack #5: Direct User Details Access

**File**: `users-table-config.resolver.ts` Lines 267-273:

```typescript
private openUser($event: Event, user: User, config: EntityTableConfig<User>) {
  if ($event) {
    $event.stopPropagation();
  }
  const url = this.router.createUrlTree([user.id.id], {relativeTo: config.getActivatedRoute()});
  this.router.navigateByUrl(url);
}
```

**The Hack**:
1. Navigates to user details using user.id.id directly
2. Creates URL like: `/customers/:customerId/users/:entityId`
3. No backend permission check before navigation
4. Relies on backend API to reject unauthorized access

**Permission Path**:
1. Frontend routes to `/customers/:customerId/users/:userId`
2. UsersTableConfigResolver runs again for entity details page
3. Loads user via `config.loadEntity = id => this.userService.getUser(id.id)`
4. Backend call to `getUserById()` finally checks permissions

---

## Backend Permission Checking

### UserController.getUserById (Lines 142-167)

```java
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
@RequestMapping(value = "/user/{userId}", method = RequestMethod.GET)
public User getUserById(
    @PathVariable(USER_ID) String strUserId) throws ThingsboardException {
  checkParameter(USER_ID, strUserId);
  UserId userId = new UserId(toUUID(strUserId));
  User user = checkUserId(userId, Operation.READ);  // CHECK PERMISSION
  // ...
  return user;
}
```

### Key Backend Check: checkUserId() (BaseController.java Lines 527-529)

```java
User checkUserId(UserId userId, Operation operation) throws ThingsboardException {
  return checkEntityId(userId, userService::findUserById, operation);
}
```

### Generic Permission Check (BaseController.java Lines 611-627)

```java
protected <E extends HasId<I> & HasTenantId, I extends EntityId> E checkEntityId(
    I entityId, 
    ThrowingBiFunction<TenantId, I, E> findingFunction, 
    Operation operation) throws ThingsboardException {
  try {
    validateId((UUIDBased) entityId, "Invalid entity id");
    SecurityUser user = getCurrentUser();
    E entity = findingFunction.apply(user.getTenantId(), entityId);  // Uses user's tenantId
    checkNotNull(entity, entityId.getEntityType().getNormalName() + " with id [" + entityId + "] is not found");
    return checkEntity(user, entity, operation);
  } catch (Exception e) {
    throw handleException(e, false);
  }
}

protected <E extends HasId<I> & HasTenantId, I extends EntityId> E checkEntity(
    SecurityUser user, E entity, Operation operation) throws ThingsboardException {
  checkNotNull(entity, "Entity not found");
  accessControlService.checkPermission(user, Resource.of(entity.getId().getEntityType()), 
    operation, entity.getId(), entity);
  return entity;
}
```

### DefaultAccessControlService Permission Check

```java
public <I extends EntityId, T extends HasTenantId> void checkPermission(
    SecurityUser user, Resource resource, Operation operation, 
    I entityId, T entity) throws ThingsboardException {
  PermissionChecker permissionChecker = getPermissionChecker(user.getAuthority(), resource);
  if (!permissionChecker.hasPermission(user, operation, entityId, entity)) {
    permissionDenied();  // Throws exception
  }
}
```

**Critical Insight**:
- Permission checking is AUTHORITY-BASED only, not UserRole-based
- ADMIN users have Authority.TENANT_ADMIN so they pass the authority check
- The system doesn't have fine-grained permission rules for ADMIN vs CONTROLYTICS_ADMIN
- **This is where the security vulnerability exists** - ADMIN and CONTROLYTICS_ADMIN both use Authority.TENANT_ADMIN but should have different access levels

---

## Delete Action Workflow

### Hack #6: Role-Based Delete Visibility (Lines 102-114)

```typescript
this.config.deleteEnabled = user => {
  // Prevent self-deletion
  if (!user || !user.id || user.id.id === this.authState.userDetails.id.id) {
    return false;
  }

  // Only CONTROLYTICS_ADMIN or SYS_ADMIN can delete users
  const currentUserRole = this.authState.userDetails.additionalInfo?.role;
  const currentAuthority = this.authState.userDetails.authority;

  return currentUserRole === UserRole.CONTROLYTICS_ADMIN ||
         currentAuthority === Authority.SYS_ADMIN;
};
```

**The Hack**:
1. Delete buttons are **purely frontend-hidden** for ADMIN users
2. If ADMIN user deletes the visibility check and calls delete API, backend ALSO checks
3. Backend PreAuthorize on deleteUser requires Authority.SYS_ADMIN or Authority.TENANT_ADMIN
4. ADMIN users have Authority.TENANT_ADMIN so backend would ALLOW the delete

**Dual Validation Pattern**:
1. **Frontend**: Hides delete button for ADMIN users (UserRole check)
2. **Backend**: Would allow delete if ADMIN directly calls API (Authority check)
3. **True Security**: Only enforced at backend through AccessControlService checking TenantAdminPermissions

---

## Backend Delete Security (UserController.java Lines 378-394)

```java
@ApiOperation(value = "Delete User")
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")  // BOTH can delete!
@RequestMapping(value = "/user/{userId}", method = RequestMethod.DELETE)
@ResponseStatus(value = HttpStatus.OK)
public void deleteUser(
    @PathVariable(USER_ID) String strUserId) throws ThingsboardException {
  checkParameter(USER_ID, strUserId);
  UserId userId = new UserId(toUUID(strUserId));
  User user = checkUserId(userId, Operation.DELETE);
  if (user.getAuthority() == Authority.SYS_ADMIN && getCurrentUser().getId().equals(userId)) {
    throw new ThingsboardException("Sysadmin is not allowed to delete himself", 
      ThingsboardErrorCode.PERMISSION_DENIED);
  }
  tbUserService.delete(getTenantId(), getCurrentUser().getCustomerId(), user, getCurrentUser());
}
```

**Security Analysis**:
- PreAuthorize allows both SYS_ADMIN and TENANT_ADMIN
- ADMIN users have TENANT_ADMIN authority, so they CAN delete
- No UserRole-based backend check exists
- **This is likely an intentional design decision** - ADMIN has delete capability via Authority, but it's hidden in UI

---

## Re-authentication Workaround for Sensitive Operations

### Hack #7: Frontend Re-authentication Gate

**Multiple Operations Require Re-auth for ADMIN**:
1. saveUser (Lines 197-233)
2. displayTemporaryPassword (Lines 341-361)
3. setUserCredentialsEnabled (Lines 396-416)
4. addUser (in add-user-dialog.component.ts Lines 91-105)

**Example Pattern** (displayTemporaryPassword):

```typescript
displayTemporaryPassword($event: Event, user: User) {
  if (this.authUser.additionalInfo?.role === UserRole.ADMIN) {
    this.dialogService.relogin({...}).subscribe((result) => {
      if (result && result.reloginStatus) {
        this.performDisplayTemporaryPassword(user);
      }
    });
  } else {
    this.performDisplayTemporaryPassword(user);
  }
}
```

**Why This Hack Exists**:
1. ADMIN users manage users across a customer domain
2. Re-authentication ensures ADMIN is actively authorizing sensitive operations
3. CONTROLYTICS_ADMIN (SYS_ADMIN) doesn't need this - higher trust level
4. This is **audit/security theater** - doesn't prevent determined attack

---

## URL Navigation Pattern for ADMIN

### How ADMIN Views Other Users

**Route**: `/customers/:customerId/users`

**URL Patterns ADMIN Can Access**:
- `/customers/{customerId}/users` - View all users for a customer
- `/customers/{customerId}/users/{userId}` - View specific user details

**No Hard Validation That ADMIN Owns Customer**:
- System trusts ADMIN to navigate to correct customer
- If ADMIN knows another customer's ID, they could navigate there
- Backend would (hopefully) reject unauthorized access

### Missing Guard: No Backend Ownership Check

The system assumes:
1. Frontend routing logic prevents ADMIN from accessing wrong customers
2. Backend permission checks handle the edge cases
3. There's NO explicit "ADMIN can only manage customer X" validation

---

## Security Vulnerabilities & Workarounds Summary

### Vulnerability #1: Frontend-Driven Permission Boundaries
- **Issue**: CustomerId from route params is not validated
- **Mitigation**: Backend API should validate ADMIN's association with customer
- **Current Risk**: Moderate - if ADMIN knows another customer's ID, could potentially access users

### Vulnerability #2: Dual Authority Checking
- **Issue**: ADMIN and CONTROLYTICS_ADMIN both use Authority.TENANT_ADMIN
- **Mitigation**: Backend permission checking doesn't distinguish between them
- **Current Risk**: Moderate - ADMIN could (via direct API calls) perform SYS_ADMIN operations

### Vulnerability #3: Re-authentication Purely Frontend
- **Issue**: Re-authentication dialog doesn't validate permission changes
- **Mitigation**: Serves more as UX confirmation than security gate
- **Current Risk**: Low - determined attacker could bypass with intercepted tokens

### Vulnerability #4: No Audit Trail for UserRole Changes
- **Issue**: When ADMIN saves a user, authority is rewritten but not audited
- **Mitigation**: Should log when authority is escalated/modified
- **Current Risk**: Low to Moderate - privilege escalation not tracked

---

## Recommendations for Hardening

1. **Add Backend CustomerId Validation**
   - Verify ADMIN user is assigned to the customer in request
   - Database table: ADMIN -> Customer associations

2. **Implement UserRole-Based Backend Permissions**
   - Add TenantAdminPermissions class that checks UserRole
   - Distinguish ADMIN from CONTROLYTICS_ADMIN in permission checking

3. **Add Audit Logging**
   - Log all user authority changes
   - Log all sensitive operations by ADMIN (re-auth trigger)
   - Log any cross-customer access attempts

4. **Strengthen Re-authentication**
   - Backend should validate re-authentication token
   - Limit sensitive operations to specific time window after re-auth

5. **Implement RBAC at Backend**
   - Current system is solely Authority-based
   - Add UserRole dimension to all permission checks

---

## Conclusion

The ADMIN role implementation relies heavily on:
1. **Frontend routing trusting the customerId parameter**
2. **Authority-based backend permissions (no UserRole checking)**
3. **Frontend UI hiding for UserRole restrictions**
4. **Re-authentication providing psychological, not cryptographic security**

The system prioritizes **usability** (allowing ADMIN to manage customer users) over **strict permission boundaries**. This requires strong **backend validation** to prevent privilege escalation attacks.

