# ADMIN Role Implementation - Complete File Reference with Absolute Paths

## Frontend Files (TypeScript/Angular)

### 1. PRIMARY: Users Table Configuration Resolver
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`

**Key Code Sections**:
- **Lines 134-149**: ADMIN role special handling - Multi-API fusion hack
- **Lines 197-233**: Re-authentication gate for user save (HACK #3)
- **Lines 235-246**: Authority override in performSaveUser (HACK #4)
- **Lines 267-273**: Direct user details navigation (HACK #5)
- **Lines 102-114**: Delete action visibility (HACK #6)
- **Lines 341-361**: Re-authentication for temporary password display (HACK #7)
- **Lines 396-416**: Re-authentication for user credentials enable/disable

### 2. User Dialog Component (Add User)
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/user/add-user-dialog.component.ts`

**Key Code Sections**:
- **Lines 91-112**: ADMIN re-authentication for user creation (HACK #7)
- **Lines 114-124**: Authority override logic (HACK #4)
- **Lines 125-150**: Activation method handling

### 3. User Model Definition
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/shared/models/user.model.ts`

**Key Code Sections**:
- **Lines 35-41**: UserRole enum
  - ADMIN = 'admin'
  - OPERATOR = 'operator'
  - MAINTENANCE = 'maintenance'
  - SUPERVISOR = 'supervisor'
  - CONTROLYTICS_ADMIN = 'controlytics_admin'
- **Lines 76-87**: AuthUser interface

### 4. Customer Routing Module
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/customer/customer-routing.module.ts`

**Key Code Sections**:
- **Lines 69, 107, 161, 177, 199**: Auth restriction to Authority.TENANT_ADMIN
- **Line 149**: Route pattern `:customerId/users`
- **Lines 156-184**: Users child routes

### 5. User Component (Details Page)
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/user/user.component.ts`

**Relevant Sections**:
- Uses `entitiesTableConfig.deleteEnabled()` for delete visibility
- Inherits permission logic from UsersTableConfigResolver

---

## Backend Files (Java)

### 1. User Controller
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/UserController.java`

**Key Methods**:

#### getUserById() - Lines 142-167
```
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
@RequestMapping(value = "/user/{userId}", method = RequestMethod.GET)
public User getUserById(
    @Parameter(description = USER_ID_PARAM_DESCRIPTION)
    @PathVariable(USER_ID) String strUserId) throws ThingsboardException
```
**What it does**: Fetches a single user by ID, checks permissions

#### getTenantAdmins() - Lines 465-482
```
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/tenant/{tenantId}/users", params = {"pageSize", "page"}, method = RequestMethod.GET)
public PageData<User> getTenantAdmins(
    @Parameter(description = TENANT_ID_PARAM_DESCRIPTION, required = true)
    @PathVariable(TENANT_ID) String strTenantId, ...)
```
**What it does**: Fetches all tenant-level admin users (Authority.TENANT_ADMIN)

#### getCustomerUsers() - Lines 489-508
```
@PreAuthorize("hasAuthority('TENANT_ADMIN')")
@RequestMapping(value = "/customer/{customerId}/users", params = {"pageSize", "page"}, method = RequestMethod.GET)
public PageData<User> getCustomerUsers(
    @Parameter(description = CUSTOMER_ID_PARAM_DESCRIPTION, required = true)
    @PathVariable(CUSTOMER_ID) String strCustomerId, ...)
```
**What it does**: Fetches customer-specific users (Authority.CUSTOMER_USER)

#### deleteUser() - Lines 378-394
```
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/user/{userId}", method = RequestMethod.DELETE)
public void deleteUser(
    @Parameter(description = USER_ID_PARAM_DESCRIPTION)
    @PathVariable(USER_ID) String strUserId) throws ThingsboardException
```
**What it does**: Deletes user (allows both SYS_ADMIN and TENANT_ADMIN)

#### saveUser() - Lines 204-225
```
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
@RequestMapping(value = "/user", method = RequestMethod.POST)
public User saveUser(
    @Parameter(description = "A JSON value representing the User.", required = true)
    @RequestBody User user, ...) throws ThingsboardException
```
**What it does**: Creates or updates user, calls TbUserService.save()

#### setUserCredentialsEnabled() - Lines 510-551
```
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/user/{userId}/userCredentialsEnabled", method = RequestMethod.POST)
public void setUserCredentialsEnabled(
    @Parameter(description = USER_ID_PARAM_DESCRIPTION)
    @PathVariable(USER_ID) String strUserId, ...) throws ThingsboardException
```
**What it does**: Enables or disables user credentials, logs audit trail

#### getTemporaryPassword() - Lines 299-334
```
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/user/{userId}/temporaryPassword", method = RequestMethod.GET, produces = "text/plain")
public String getTemporaryPassword(
    @Parameter(description = USER_ID_PARAM_DESCRIPTION)
    @PathVariable(USER_ID) String strUserId, ...) throws ThingsboardException
```
**What it does**: Returns temporary password, logs audit trail (lines 314-327)

### 2. Base Controller
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/BaseController.java`

**Key Methods**:

#### checkUserId() - Lines 527-529
```java
User checkUserId(UserId userId, Operation operation) throws ThingsboardException {
  return checkEntityId(userId, userService::findUserById, operation);
}
```
**What it does**: Checks user permission and returns user

#### checkCustomerId() - Lines 523-525
```java
Customer checkCustomerId(CustomerId customerId, Operation operation) throws ThingsboardException {
  return checkEntityId(customerId, customerService::findCustomerById, operation);
}
```
**What it does**: Checks customer permission and returns customer

#### checkEntityId() - Lines 611-627
```java
protected <E extends HasId<I> & HasTenantId, I extends EntityId> E checkEntityId(
    I entityId, 
    ThrowingBiFunction<TenantId, I, E> findingFunction, 
    Operation operation) throws ThingsboardException {
  try {
    validateId((UUIDBased) entityId, "Invalid entity id");
    SecurityUser user = getCurrentUser();
    E entity = findingFunction.apply(user.getTenantId(), entityId);  // Uses user's tenantId
    checkNotNull(entity, ...);
    return checkEntity(user, entity, operation);
  }
}
```
**What it does**: Generic entity check - fetches entity using user's tenantId

#### checkEntity() - Lines 623-627
```java
protected <E extends HasId<I> & HasTenantId, I extends EntityId> E checkEntity(
    SecurityUser user, E entity, Operation operation) throws ThingsboardException {
  checkNotNull(entity, "Entity not found");
  accessControlService.checkPermission(user, Resource.of(entity.getId().getEntityType()), 
    operation, entity.getId(), entity);
  return entity;
}
```
**What it does**: Calls AccessControlService to check permission

### 3. Access Control Service (Interface)
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/service/security/permission/AccessControlService.java`

```java
public interface AccessControlService {
  void checkPermission(SecurityUser user, Resource resource, Operation operation) throws ThingsboardException;
  
  <I extends EntityId, T extends HasTenantId> void checkPermission(
    SecurityUser user, Resource resource, Operation operation, I entityId, T entity) throws ThingsboardException;
}
```

### 4. Default Access Control Service (Implementation)
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/service/security/permission/DefaultAccessControlService.java`

**Key Methods**:

#### checkPermission() - Lines 50-66
```java
@Override
@SuppressWarnings("unchecked")
public <I extends EntityId, T extends HasTenantId> void checkPermission(
    SecurityUser user, Resource resource, Operation operation, I entityId, T entity) 
    throws ThingsboardException {
  PermissionChecker permissionChecker = getPermissionChecker(user.getAuthority(), resource);
  if (!permissionChecker.hasPermission(user, operation, entityId, entity)) {
    permissionDenied();
  }
}
```
**Critical**: Only checks `user.getAuthority()` - NO UserRole checking!

#### getPermissionChecker() - Lines 68-78
```java
private PermissionChecker getPermissionChecker(Authority authority, Resource resource) throws ThingsboardException {
  Permissions permissions = authorityPermissions.get(authority);  // Maps Authority to Permissions
  if (permissions == null) {
    permissionDenied();
  }
  Optional<PermissionChecker> permissionChecker = permissions.getPermissionChecker(resource);
  if (!permissionChecker.isPresent()) {
    permissionDenied();
  }
  return permissionChecker.get();
}
```
**Critical**: Authority-based lookup only, no UserRole consideration

### 5. TbUserService (Interface)
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/service/entitiy/user/TbUserService.java`

```java
public interface TbUserService {
  User save(TenantId tenantId, CustomerId customerId, User tbUser, 
    boolean sendActivationMail, HttpServletRequest request, User user) throws ThingsboardException;
  
  void delete(TenantId tenantId, CustomerId customerId, User user, User responsibleUser) throws ThingsboardException;
}
```

### 6. DefaultUserService (Implementation)
**Absolute Path**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/service/entitiy/user/DefaultUserService.java`

**Key Methods**:

#### save() - Lines 49-75
```java
@Override
public User save(TenantId tenantId, CustomerId customerId, User tbUser, boolean sendActivationMail,
  HttpServletRequest request, User user) throws ThingsboardException {
  // ...
  User savedUser = checkNotNull(userService.saveUser(tenantId, tbUser));
  if (sendEmail) {
    // Send activation email
  }
  logEntityActionService.logEntityAction(...);  // Audit trail
  return savedUser;
}
```
**What it does**: Saves user and sends activation email if needed

#### delete() - Lines 77-90
```java
@Override
public void delete(TenantId tenantId, CustomerId customerId, User user, User responsibleUser) throws ThingsboardException {
  ActionType actionType = ActionType.DELETED;
  UserId userId = user.getId();
  
  try {
    userService.deleteUser(tenantId, user);
    logEntityActionService.logEntityAction(...);  // Audit trail
  }
}
```
**What it does**: Deletes user and logs audit trail

---

## Key Files & Their Roles

### Frontend Permission Enforcement
1. **customer-routing.module.ts** - Restricts routes to Authority.TENANT_ADMIN
2. **users-table-config.resolver.ts** - Implements ADMIN-specific logic and hacks
3. **add-user-dialog.component.ts** - Re-authentication and authority override

### Backend Permission Enforcement
1. **UserController.java** - @PreAuthorize annotations (Authority-based)
2. **BaseController.java** - Generic entity permission checking
3. **DefaultAccessControlService.java** - Authority-only permission logic (no UserRole)
4. **DefaultUserService.java** - Audit logging for user operations

### Data Models
1. **user.model.ts** - UserRole and Authority enums

---

## Critical Code Snippets by Hack

### HACK #1 & #2: Multi-API Fusion and CustomerId Passthrough
**File**: `users-table-config.resolver.ts`
**Lines**: 134-149
```typescript
if (this.authUser.additionalInfo.role === UserRole.ADMIN) {
  this.tenantId = this.authUser.tenantId.id;
  this.customerId = routeParams.customerId;  // No validation!
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

### HACK #3: Re-authentication Gate
**File**: `users-table-config.resolver.ts`
**Lines**: 197-233
```typescript
saveUser(user: User): Observable<User> {
  if (this.authUser.additionalInfo?.role === UserRole.ADMIN) {
    return new Observable(observer => {
      this.dialogService.relogin({
        remarksRequired: false,
        intervalRequired: false,
        timeRangeRequired: false,
        userNameInputRequired: false
      } as ReLoginDialogComponentData).subscribe(
        (result) => {
          if (result && result.reloginStatus) {
            this.performSaveUser(user).subscribe(
              response => {
                observer.next(response);
                observer.complete();
              },
              error => { observer.error(error); }
            );
          } else {
            observer.error(new Error('Re-authentication failed'));
          }
        }
      );
    });
  } else {
    return this.performSaveUser(user);
  }
}
```

### HACK #4: Authority Override
**File**: `users-table-config.resolver.ts`
**Lines**: 235-246
```typescript
private performSaveUser(user: User): Observable<User> {
  user.tenantId = new TenantId(this.tenantId);
  user.customerId = new CustomerId(this.customerId);
  if (user.additionalInfo.role === UserRole.ADMIN
    || user.additionalInfo.role === UserRole.MAINTENANCE) {
    user.authority = Authority.TENANT_ADMIN;
    user.customerId = undefined;
  } else {
    user.authority = this.authority;
  }
  return this.userService.saveUser(user);
}
```

### HACK #6: Delete Visibility
**File**: `users-table-config.resolver.ts`
**Lines**: 102-114
```typescript
this.config.deleteEnabled = user => {
  if (!user || !user.id || user.id.id === this.authState.userDetails.id.id) {
    return false;
  }

  const currentUserRole = this.authState.userDetails.additionalInfo?.role;
  const currentAuthority = this.authState.userDetails.authority;

  return currentUserRole === UserRole.CONTROLYTICS_ADMIN ||
         currentAuthority === Authority.SYS_ADMIN;
};
```

---

## Summary of Files to Review for Security Audit

**Priority 1 (Critical)**:
- `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`
- `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/service/security/permission/DefaultAccessControlService.java`
- `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/UserController.java`

**Priority 2 (Important)**:
- `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/BaseController.java`
- `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/customer/customer-routing.module.ts`
- `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/user/add-user-dialog.component.ts`

**Priority 3 (Context)**:
- `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/shared/models/user.model.ts`
- `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/service/entitiy/user/DefaultUserService.java`

