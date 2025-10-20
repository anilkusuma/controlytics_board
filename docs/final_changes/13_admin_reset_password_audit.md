# Requirement #13: Admin Reset Password Audit Trail

## Requirement

When an Admin receives a forgot password request and resets a user's password (either by disabling the user account or generating a temporary password/reset token), this administrative action should be recorded in the audit trail for security and compliance tracking.

### Current Issues:
- When Admin generates a temporary password for a user, this action is NOT recorded in the audit trail
- When Admin gets a reset password link/token for a user, this action is NOT recorded in the audit trail
- When Admin disables/enables user credentials, this action is NOT properly recorded with sufficient context
- This creates a security and compliance gap as sensitive password reset operations can be performed without proper audit tracking

### Context:
- Admins can reset user passwords through several methods in the user management interface:
  1. **Display Temporary Password** - Admin generates and views a temporary password for the user
  2. **Display Reset Password Link** - Admin generates and views a password reset link/token
  3. **Disable User Account** - Admin disables user credentials (related to password management)
  4. **Enable User Account** - Admin re-enables user credentials
- These actions are triggered from the User Details page or Users table
- Located in: Users > User Details page > Action buttons
- Different from user self-service password reset which is already logged with `CREDENTIALS_RESET_REQUEST` action type

---

## Current Implementation as per Code

### Backend Components

#### 1. UserController - Admin Password Reset Operations
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/UserController.java`

**Lines 296-316**: Get Temporary Password - NO audit logging
```java
@ApiOperation(value = "Get temporary password",
        notes = "Get temporary password for the user. " + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/user/{userId}/temporaryPassword", method = RequestMethod.GET, produces = "text/plain")
@ResponseBody
public String getTemporaryPassword(
        @Parameter(description = USER_ID_PARAM_DESCRIPTION)
        @PathVariable(USER_ID) String strUserId,
        HttpServletRequest request) throws ThingsboardException {
    checkParameter(USER_ID, strUserId);
    UserId userId = new UserId(toUUID(strUserId));
    User user = checkUserId(userId, Operation.READ);
    SecurityUser authUser = getCurrentUser();
    UserCredentials userCredentials = userService.findUserCredentialsByUserId(authUser.getTenantId(), user.getId());
    if (userCredentials.getResetToken() != null) {
        return userCredentials.getResetToken();
    } else {
        throw new ThingsboardException("User did not request for reset password!",
                ThingsboardErrorCode.BAD_REQUEST_PARAMS);
    }
}
```

**Lines 318-342**: Get Reset Password Link - NO audit logging
```java
@ApiOperation(value = "Get the reset password link",
        notes = "Get the reset password link for the user. " +
                "The base url for activation link is configurable in the general settings of system administrator. " + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/user/{userId}/resetPasswordLink", method = RequestMethod.GET, produces = "text/plain")
@ResponseBody
public String getResetPasswordLink(
        @Parameter(description = USER_ID_PARAM_DESCRIPTION)
        @PathVariable(USER_ID) String strUserId,
        HttpServletRequest request) throws ThingsboardException {
    checkParameter(USER_ID, strUserId);
    UserId userId = new UserId(toUUID(strUserId));
    User user = checkUserId(userId, Operation.READ);
    SecurityUser authUser = getCurrentUser();
    UserCredentials userCredentials = userService.findUserCredentialsByUserId(authUser.getTenantId(), user.getId());
    if (userCredentials.getResetToken() != null) {
        String baseUrl = systemSecurityService.getBaseUrl(getTenantId(), getCurrentUser().getCustomerId(), request);
        String activateUrl = String.format(RESET_PASSWORD_URL_PATTERN, baseUrl,
                userCredentials.getResetToken());
        return activateUrl;
    } else {
        throw new ThingsboardException("User did not request for reset password!",
                ThingsboardErrorCode.BAD_REQUEST_PARAMS);
    }
}
```

**Lines 476-495**: Set User Credentials Enabled - NO audit logging
```java
@ApiOperation(value = "Enable/Disable User credentials (setUserCredentialsEnabled)",
        notes = "Enables or Disables user credentials. Useful when you would like to block user account without deleting it. " + PAGE_DATA_PARAMETERS + TENANT_AUTHORITY_PARAGRAPH)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/user/{userId}/userCredentialsEnabled", method = RequestMethod.POST)
@ResponseBody
public void setUserCredentialsEnabled(
        @Parameter(description = USER_ID_PARAM_DESCRIPTION)
        @PathVariable(USER_ID) String strUserId,
        @Parameter(description = "Enable (\"true\") or disable (\"false\") the credentials." , schema = @Schema(defaultValue = "true"))
        @RequestParam(required = false, defaultValue = "true") boolean userCredentialsEnabled) throws ThingsboardException {
    checkParameter(USER_ID, strUserId);
    UserId userId = new UserId(toUUID(strUserId));
    User user = checkUserId(userId, Operation.WRITE);
    TenantId tenantId = getCurrentUser().getTenantId();
    userService.setUserCredentialsEnabled(tenantId, userId, userCredentialsEnabled);

    if (!userCredentialsEnabled) {
        eventPublisher.publishEvent(new UserCredentialsInvalidationEvent(userId));
    }
}
```

**Current Behavior**:
- None of these admin password reset operations log to audit trail
- The only password-related audit logging is for user self-service password reset (`CREDENTIALS_RESET_REQUEST` in AuthController)
- Admin actions on user passwords are completely untracked

#### 2. UserServiceImpl - User Credentials Management
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/dao/src/main/java/org/thingsboard/server/dao/user/UserServiceImpl.java`

**Lines 254-268**: Request Password Reset (User Self-Service) - Has audit logging via ActionEntityEvent
```java
@Override
public UserCredentials requestPasswordReset(TenantId tenantId, String email) {
    log.trace("Executing requestPasswordReset email [{}]", email);
    DataValidator.validateEmail(email);
    User user = findUserByEmail(tenantId, email);
    if (user == null) {
        throw new UsernameNotFoundException(String.format("Unable to find user by email [%s]", email));
    }
    UserCredentials userCredentials = userCredentialsDao.findByUserId(tenantId, user.getUuidId());
    if (!userCredentials.isEnabled()) {
        throw new DisabledException(String.format("User credentials not enabled [%s]", email));
    }
    userCredentials.setResetToken(generatePasswordAsPerPolicy());
    return saveUserCredentials(tenantId, userCredentials);
}
```

**Lines 222-231**: Save User Credentials - Publishes ActionEntityEvent with CREDENTIALS_UPDATED
```java
@Override
public UserCredentials saveUserCredentials(TenantId tenantId, UserCredentials userCredentials) {
    log.trace("Executing saveUserCredentials [{}]", userCredentials);
    userCredentialsValidator.validate(userCredentials, data -> tenantId);
    UserCredentials result = userCredentialsDao.save(tenantId, userCredentials);
    eventPublisher.publishEvent(ActionEntityEvent.builder()
            .tenantId(tenantId)
            .entityId(userCredentials.getUserId())
            .actionType(ActionType.CREDENTIALS_UPDATED).build());
    return result;
}
```

**Lines 409-427**: Set User Credentials Enabled - NO audit logging
```java
@Override
public void setUserCredentialsEnabled(TenantId tenantId, UserId userId, boolean enabled) {
    log.trace("Executing setUserCredentialsEnabled [{}], [{}]", userId, enabled);
    validateId(userId, id -> INCORRECT_USER_ID + id);
    UserCredentials userCredentials = userCredentialsDao.findByUserId(tenantId, userId.getId());
    userCredentials.setEnabled(enabled);
    saveUserCredentials(tenantId, userCredentials);

    User user = findUserById(tenantId, userId);
    JsonNode additionalInfo = user.getAdditionalInfo();
    if (!(additionalInfo instanceof ObjectNode)) {
        additionalInfo = JacksonUtil.newObjectNode();
    }
    ((ObjectNode) additionalInfo).put(USER_CREDENTIALS_ENABLED, enabled);
    user.setAdditionalInfo(additionalInfo);
    if (enabled) {
        resetFailedLoginAttempts(user);
    }
    userDao.save(user.getTenantId(), user);
}
```

**Current Behavior**:
- `saveUserCredentials()` publishes an `ActionEntityEvent` with `CREDENTIALS_UPDATED` action type
- This is generic and doesn't distinguish between admin-initiated password reset vs other credential updates
- `setUserCredentialsEnabled()` calls `saveUserCredentials()` but doesn't provide context about who initiated the action
- The audit trail would show "credentials updated" but not that it was an admin-initiated password reset

#### 3. ActionType Enum - Available Audit Action Types
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/common/data/src/main/java/org/thingsboard/server/common/data/audit/ActionType.java`

**Lines 23-62**: Action Types
```java
public enum ActionType {
    ADDED(false, TbMsgType.ENTITY_CREATED), // log entity
    DELETED(false, TbMsgType.ENTITY_DELETED), // log string id
    UPDATED(false, TbMsgType.ENTITY_UPDATED), // log entity
    ATTRIBUTES_UPDATED(false, TbMsgType.ATTRIBUTES_UPDATED), // log attributes/values
    ATTRIBUTES_DELETED(false, TbMsgType.ATTRIBUTES_DELETED), // log attributes
    TIMESERIES_UPDATED(false, TbMsgType.TIMESERIES_UPDATED), // log timeseries update
    TIMESERIES_DELETED(false, TbMsgType.TIMESERIES_DELETED), // log timeseries
    RPC_CALL(false, null), // log method and params
    CREDENTIALS_RESET_REQUEST(false, TbMsgType.ENTITY_UPDATED),
    CREDENTIALS_UPDATED(false, null), // log new credentials
    ASSIGNED_TO_CUSTOMER(false, TbMsgType.ENTITY_ASSIGNED), // log customer name
    UNASSIGNED_FROM_CUSTOMER(false, TbMsgType.ENTITY_UNASSIGNED), // log customer name
    ACTIVATED(false, null), // log string id
    SUSPENDED(false, null), // log string id
    // ... more action types
}
```

**Current Behavior**:
- `CREDENTIALS_RESET_REQUEST` exists and is used for user self-service password reset requests
- `CREDENTIALS_UPDATED` exists for generic credential updates
- No specific action type for admin-initiated password reset operations
- `SUSPENDED` and `ACTIVATED` could potentially be used for enable/disable operations but aren't currently used

### Frontend Components

#### 4. Users Table Config Resolver - Admin Actions
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`

**Lines 353-393**: Display Temporary Password with Re-authentication
```typescript
displayTemporaryPassword($event: Event, user: User) {
  if ($event) {
    $event.stopPropagation();
  }
  // Check if current user is Admin (not Controlytics Admin)
  if (this.authUser.additionalInfo?.role === UserRole.ADMIN) {
    // Show re-authentication dialog for Admin users
    this.dialogService.relogin({
      remarksRequired: false,
      intervalRequired: false,
      timeRangeRequired: false,
      userNameInputRequired: false
    } as ReLoginDialogComponentData).subscribe(
      (result) => {
        if (result && result.reloginStatus) {
          // Proceed with displaying temporary password after successful re-authentication
          this.performDisplayTemporaryPassword(user);
        }
      }
    );
  } else {
    // Skip re-authentication for Controlytics Admin or other roles
    this.performDisplayTemporaryPassword(user);
  }
}

private performDisplayTemporaryPassword(user: User) {
  this.userService.getTemporaryPassword(user.id.id).subscribe(
    (temporaryPassword) => {
      this.dialog.open<ActivationLinkDialogComponent, ActivationLinkDialogData,
        void>(ActivationLinkDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          activationLink: temporaryPassword,
          isTemporaryPassword: true
        }
      });
    }
  );
}
```

**Lines 408-446**: Set User Credentials Enabled with Re-authentication
```typescript
setUserCredentialsEnabled($event: Event, user: User, userCredentialsEnabled: boolean) {
  if ($event) {
    $event.stopPropagation();
  }
  // Check if current user is Admin (not Controlytics Admin)
  if (this.authUser.additionalInfo?.role === UserRole.ADMIN) {
    // Show re-authentication dialog for Admin users
    this.dialogService.relogin({
      remarksRequired: false,
      intervalRequired: false,
      timeRangeRequired: false,
      userNameInputRequired: false
    } as ReLoginDialogComponentData).subscribe(
      (result) => {
        if (result && result.reloginStatus) {
          // Proceed with enabling/disabling user after successful re-authentication
          this.performSetUserCredentialsEnabled(user, userCredentialsEnabled);
        }
      }
    );
  } else {
    // Skip re-authentication for Controlytics Admin or other roles
    this.performSetUserCredentialsEnabled(user, userCredentialsEnabled);
  }
}

private performSetUserCredentialsEnabled(user: User, userCredentialsEnabled: boolean) {
  this.userService.setUserCredentialsEnabled(user.id.id, userCredentialsEnabled).subscribe(() => {
    if (!user.additionalInfo) {
      user.additionalInfo = {};
    }
    user.additionalInfo.userCredentialsEnabled = userCredentialsEnabled;
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant(userCredentialsEnabled ? 'user.enable-account-message' : 'user.disable-account-message'),
        type: 'success'
      }));
  });
}
```

**Lines 335-351**: Display Reset Password Link - NO re-authentication required
```typescript
displayResetPasswordLink($event: Event, user: User) {
  if ($event) {
    $event.stopPropagation();
  }
  this.userService.getResetPasswordLink(user.id.id).subscribe(
    (resetPasswordLink) => {
      this.dialog.open<ActivationLinkDialogComponent, ActivationLinkDialogData,
        void>(ActivationLinkDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          activationLink: resetPasswordLink
        }
      });
    }
  );
}
```

**Current Behavior**:
- Display Temporary Password requires re-authentication for Admin users
- Set User Credentials Enabled requires re-authentication for Admin users
- Display Reset Password Link does NOT require re-authentication
- None of these actions trigger audit trail logging from the frontend

#### 5. AuditLogService - Audit Logging Interface
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/common/dao-api/src/main/java/org/thingsboard/server/dao/audit/AuditLogService.java`

**Lines 46-55**: Log Entity Action Method
```java
<E extends HasName, I extends EntityId> ListenableFuture<Void> logEntityAction(
        TenantId tenantId,
        CustomerId customerId,
        UserId userId,
        String userName,
        I entityId,
        E entity,
        ActionType actionType,
        Exception e, Object... additionalInfo);
```

**Current Behavior**:
- Standard interface for logging entity actions exists
- Supports recording admin user who performed the action
- Can accept additional contextual information
- Not being called from admin password reset operations

---

## Expectation after the change

### 1. Audit Trail Recording Requirements

All admin-initiated password reset operations should be recorded in the audit trail with the following information:

#### For "Display Temporary Password" Action:
- **Action Type**: New action type to differentiate from user self-service - could use existing `CREDENTIALS_UPDATED` with additional context or create new type
- **Entity**: The User whose password was reset
- **Entity ID**: UserId of the target user
- **User ID**: Admin who performed the action
- **User Name**: Email/name of admin who performed the action
- **Tenant ID**: Tenant context
- **Customer ID**: Customer context (if applicable)
- **Additional Info**:
  - Action description: "Admin retrieved temporary password for user"
  - Whether re-authentication was performed
  - User email whose password was accessed

#### For "Display Reset Password Link" Action:
- **Action Type**: Similar to temporary password action
- **Entity**: The User whose reset link was generated
- **Entity ID**: UserId of the target user
- **User ID**: Admin who performed the action
- **User Name**: Email/name of admin who performed the action
- **Additional Info**:
  - Action description: "Admin retrieved password reset link for user"
  - User email whose reset link was accessed

#### For "Disable User Account" Action:
- **Action Type**: Could use `SUSPENDED` or enhanced `CREDENTIALS_UPDATED` with context
- **Entity**: The User whose account was disabled
- **Entity ID**: UserId of the target user
- **User ID**: Admin who performed the action
- **User Name**: Email/name of admin who performed the action
- **Additional Info**:
  - Action description: "Admin disabled user credentials"
  - User email whose account was disabled
  - Whether re-authentication was performed

#### For "Enable User Account" Action:
- **Action Type**: Could use `ACTIVATED` or enhanced `CREDENTIALS_UPDATED` with context
- **Entity**: The User whose account was enabled
- **Entity ID**: UserId of the target user
- **User ID**: Admin who performed the action
- **User Name**: Email/name of admin who performed the action
- **Additional Info**:
  - Action description: "Admin enabled user credentials"
  - User email whose account was enabled
  - Whether re-authentication was performed

### 2. Audit Trail Visibility

The audit trail entries should be:
- Visible in the main Audit Logs page
- Filterable by action type
- Searchable by user email and admin email
- Include timestamps
- Exportable for compliance reporting

### 3. Distinction from User Self-Service

The audit trail must clearly distinguish between:
- **User self-service password reset**: User initiates "Forgot Password" flow (already logged as `CREDENTIALS_RESET_REQUEST`)
- **Admin-initiated password reset**: Admin generates temporary password or reset link for user (currently NOT logged - needs to be added)
- **Admin account management**: Admin disables/enables user account (currently logged as generic `CREDENTIALS_UPDATED` - needs better context)

---

## Changes required

### Backend Changes

#### 1. Add Audit Logging to UserController Methods

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/UserController.java`

**Change 1**: Inject AuditLogService or TbLogEntityActionService
```java
@RequiredArgsConstructor
@RestController
@TbCoreComponent
@RequestMapping("/api")
public class UserController extends BaseController {
    // ... existing dependencies
    private final TbUserService tbUserService;

    // ADD THIS:
    @Autowired
    private AuditLogService auditLogService;
    // OR use the entity action service wrapper
    @Autowired
    private TbLogEntityActionService logEntityActionService;
```

**Change 2**: Add audit logging to getTemporaryPassword (lines 296-316)
```java
@ApiOperation(value = "Get temporary password",
        notes = "Get temporary password for the user. " + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/user/{userId}/temporaryPassword", method = RequestMethod.GET, produces = "text/plain")
@ResponseBody
public String getTemporaryPassword(
        @Parameter(description = USER_ID_PARAM_DESCRIPTION)
        @PathVariable(USER_ID) String strUserId,
        HttpServletRequest request) throws ThingsboardException {
    checkParameter(USER_ID, strUserId);
    UserId userId = new UserId(toUUID(strUserId));
    User user = checkUserId(userId, Operation.READ);
    SecurityUser authUser = getCurrentUser();
    UserCredentials userCredentials = userService.findUserCredentialsByUserId(authUser.getTenantId(), user.getId());
    if (userCredentials.getResetToken() != null) {
        // ADD AUDIT LOGGING HERE
        try {
            logEntityActionService.logEntityAction(
                authUser.getTenantId(),
                user.getId(),
                user,
                user.getCustomerId(),
                ActionType.CREDENTIALS_UPDATED,  // Or create new ADMIN_PASSWORD_RESET action type
                authUser.toUser(),
                "Admin retrieved temporary password for user: " + user.getEmail()
            );
        } catch (Exception e) {
            log.error("Failed to log audit trail for getTemporaryPassword", e);
        }

        return userCredentials.getResetToken();
    } else {
        throw new ThingsboardException("User did not request for reset password!",
                ThingsboardErrorCode.BAD_REQUEST_PARAMS);
    }
}
```

**Change 3**: Add audit logging to getResetPasswordLink (lines 318-342)
```java
@ApiOperation(value = "Get the reset password link",
        notes = "Get the reset password link for the user. " +
                "The base url for activation link is configurable in the general settings of system administrator. " + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/user/{userId}/resetPasswordLink", method = RequestMethod.GET, produces = "text/plain")
@ResponseBody
public String getResetPasswordLink(
        @Parameter(description = USER_ID_PARAM_DESCRIPTION)
        @PathVariable(USER_ID) String strUserId,
        HttpServletRequest request) throws ThingsboardException {
    checkParameter(USER_ID, strUserId);
    UserId userId = new UserId(toUUID(strUserId));
    User user = checkUserId(userId, Operation.READ);
    SecurityUser authUser = getCurrentUser();
    UserCredentials userCredentials = userService.findUserCredentialsByUserId(authUser.getTenantId(), user.getId());
    if (userCredentials.getResetToken() != null) {
        String baseUrl = systemSecurityService.getBaseUrl(getTenantId(), getCurrentUser().getCustomerId(), request);
        String activateUrl = String.format(RESET_PASSWORD_URL_PATTERN, baseUrl,
                userCredentials.getResetToken());

        // ADD AUDIT LOGGING HERE
        try {
            logEntityActionService.logEntityAction(
                authUser.getTenantId(),
                user.getId(),
                user,
                user.getCustomerId(),
                ActionType.CREDENTIALS_UPDATED,  // Or create new ADMIN_PASSWORD_RESET_LINK action type
                authUser.toUser(),
                "Admin retrieved password reset link for user: " + user.getEmail()
            );
        } catch (Exception e) {
            log.error("Failed to log audit trail for getResetPasswordLink", e);
        }

        return activateUrl;
    } else {
        throw new ThingsboardException("User did not request for reset password!",
                ThingsboardErrorCode.BAD_REQUEST_PARAMS);
    }
}
```

**Change 4**: Add audit logging to setUserCredentialsEnabled (lines 476-495)
```java
@ApiOperation(value = "Enable/Disable User credentials (setUserCredentialsEnabled)",
        notes = "Enables or Disables user credentials. Useful when you would like to block user account without deleting it. " + PAGE_DATA_PARAMETERS + TENANT_AUTHORITY_PARAGRAPH)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/user/{userId}/userCredentialsEnabled", method = RequestMethod.POST)
@ResponseBody
public void setUserCredentialsEnabled(
        @Parameter(description = USER_ID_PARAM_DESCRIPTION)
        @PathVariable(USER_ID) String strUserId,
        @Parameter(description = "Enable (\"true\") or disable (\"false\") the credentials." , schema = @Schema(defaultValue = "true"))
        @RequestParam(required = false, defaultValue = "true") boolean userCredentialsEnabled) throws ThingsboardException {
    checkParameter(USER_ID, strUserId);
    UserId userId = new UserId(toUUID(strUserId));
    User user = checkUserId(userId, Operation.WRITE);
    TenantId tenantId = getCurrentUser().getTenantId();
    SecurityUser authUser = getCurrentUser();

    userService.setUserCredentialsEnabled(tenantId, userId, userCredentialsEnabled);

    // ADD AUDIT LOGGING HERE
    try {
        ActionType actionType = userCredentialsEnabled ? ActionType.ACTIVATED : ActionType.SUSPENDED;
        String actionDescription = userCredentialsEnabled ?
            "Admin enabled user credentials for user: " + user.getEmail() :
            "Admin disabled user credentials for user: " + user.getEmail();

        logEntityActionService.logEntityAction(
            tenantId,
            user.getId(),
            user,
            user.getCustomerId(),
            actionType,
            authUser.toUser(),
            actionDescription
        );
    } catch (Exception e) {
        log.error("Failed to log audit trail for setUserCredentialsEnabled", e);
    }

    if (!userCredentialsEnabled) {
        eventPublisher.publishEvent(new UserCredentialsInvalidationEvent(userId));
    }
}
```

#### 2. Optional: Add New Action Types (if needed)

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/common/data/src/main/java/org/thingsboard/server/common/data/audit/ActionType.java`

If you want to create specific action types for admin password reset operations:

```java
public enum ActionType {
    // ... existing action types
    CREDENTIALS_RESET_REQUEST(false, TbMsgType.ENTITY_UPDATED),  // User self-service (existing)
    CREDENTIALS_UPDATED(false, null), // Generic credential update (existing)

    // ADD THESE NEW ACTION TYPES (optional):
    ADMIN_PASSWORD_RESET(false, null),  // Admin retrieved temporary password
    ADMIN_PASSWORD_RESET_LINK(false, null),  // Admin retrieved reset password link

    // Or enhance existing ones with better context in additionalInfo
    ACTIVATED(false, null), // Use for enable user account (existing)
    SUSPENDED(false, null), // Use for disable user account (existing)
    // ...
}
```

#### 3. Update Frontend Labels (if new action types added)

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/shared/models/audit-log.models.ts`

If new action types are added:

```typescript
export enum ActionType {
  // ... existing types
  CREDENTIALS_RESET_REQUEST = 'CREDENTIALS_RESET_REQUEST',
  CREDENTIALS_UPDATED = 'CREDENTIALS_UPDATED',

  // ADD THESE (if new action types created):
  ADMIN_PASSWORD_RESET = 'ADMIN_PASSWORD_RESET',
  ADMIN_PASSWORD_RESET_LINK = 'ADMIN_PASSWORD_RESET_LINK',

  ACTIVATED = 'ACTIVATED',
  SUSPENDED = 'SUSPENDED',
}

export const actionTypeTranslations = new Map<ActionType, string>([
  // ... existing translations
  [ActionType.CREDENTIALS_RESET_REQUEST, 'audit-log.type-credentials-reset-request'],
  [ActionType.CREDENTIALS_UPDATED, 'audit-log.type-credentials-updated'],

  // ADD THESE (if new action types created):
  [ActionType.ADMIN_PASSWORD_RESET, 'audit-log.type-admin-password-reset'],
  [ActionType.ADMIN_PASSWORD_RESET_LINK, 'audit-log.type-admin-password-reset-link'],

  [ActionType.ACTIVATED, 'audit-log.type-activated'],
  [ActionType.SUSPENDED, 'audit-log.type-suspended'],
]);
```

#### 4. Add Translation Keys

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/assets/locale/locale.constant-en_US.json`

If new action types are added, add translation keys:

```json
{
  "audit-log": {
    "type-credentials-reset-request": "Password Reset Request",
    "type-credentials-updated": "Credentials Updated",
    "type-admin-password-reset": "Admin Password Reset",
    "type-admin-password-reset-link": "Admin Password Reset Link",
    "type-activated": "Account Activated",
    "type-suspended": "Account Suspended"
  }
}
```

### Testing Requirements

1. **Functional Testing**:
   - Admin retrieves temporary password → Verify audit log entry created
   - Admin retrieves reset password link → Verify audit log entry created
   - Admin disables user account → Verify audit log entry created with SUSPENDED action
   - Admin enables user account → Verify audit log entry created with ACTIVATED action
   - Verify audit logs contain correct admin user info, target user info, and timestamps

2. **Audit Trail Query Testing**:
   - Search audit logs by target user email → Should find all admin actions on that user
   - Search audit logs by admin email → Should find all actions performed by that admin
   - Filter by action type → Should correctly filter admin password reset actions
   - Export audit logs → Should include all required fields

3. **Compliance Testing**:
   - Verify audit logs cannot be deleted or modified
   - Verify audit logs are retained according to configured retention policy
   - Verify audit logs capture all required information for compliance reporting

### Summary

**Implementation Approach**:
1. Use existing `TbLogEntityActionService` to add audit logging to three key methods in `UserController`
2. Use existing action types (`CREDENTIALS_UPDATED`, `ACTIVATED`, `SUSPENDED`) with enhanced additional info OR create new specific action types
3. Ensure audit logs capture: admin user, target user, action type, timestamp, and descriptive context
4. No frontend changes required beyond adding translations if new action types are created

**Benefits**:
- Full audit trail of all admin-initiated password reset operations
- Clear distinction between user self-service and admin actions
- Compliance-ready audit logging with all required information
- Consistent with existing audit logging patterns in the codebase
