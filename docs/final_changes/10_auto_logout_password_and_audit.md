# Requirement #10: Auto Logout Duration Configuration - Password Authentication and Audit Trail

## Requirement

When an admin changes the auto logout duration configuration in security settings, the system should:

1. **Ask for password authentication/confirmation** before saving the changes
2. **Record this action in the audit trail** for security and compliance tracking

### Current Issues:
- Password authentication/confirmation is NOT requested when saving auto logout duration changes
- The change is NOT recorded in the audit trail
- This creates a security gap as sensitive timeout settings can be changed without verification or tracking

### Context:
- Auto logout duration is controlled by the `refreshTokenExpTime` field in JWT settings
- This setting determines how long a user can remain logged in before automatic logout
- In the UI, this field is labeled as "Auto logout duration (sec)"
- Located in: Security Settings > Auto logout settings section
- Currently visible to all admin users

---

## Current Implementation as per Code

### Frontend Components

#### 1. Security Settings Component TypeScript
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/admin/security-settings.component.ts`

**Lines 108-132**: Password authentication for Security Settings (Password Policy)
```typescript
save(): void {
  this.store.pipe(select(selectAuth)).subscribe(auth => {
    const currentUser = auth.userDetails;
    // Check if current user is Admin (not Controlytics Admin)
    if (currentUser.additionalInfo?.role === UserRole.ADMIN) {
      // Show re-authentication dialog for Admin users
      this.dialogService.relogin({
        remarksRequired: false,
        intervalRequired: false,
        timeRangeRequired: false,
        userNameInputRequired: false
      } as ReLoginDialogComponentData).subscribe(
        (result) => {
          if (result && result.reloginStatus) {
            // Proceed with saving security settings after successful re-authentication
            this.performSaveSecuritySettings();
          }
        }
      );
    } else {
      // Skip re-authentication for Controlytics Admin or other roles
      this.performSaveSecuritySettings();
    }
  });
}
```

**Lines 141-153**: JWT Settings Save (Auto Logout Duration) - NO password authentication
```typescript
saveJwtSettings() {
  const jwtFormSettings = this.jwtSecuritySettingsFormGroup.value;
  this.confirmChangeJWTSettings().pipe(mergeMap(value => {
    if (value) {
      return this.adminService.saveJwtSettings(jwtFormSettings).pipe(
        tap((data) => this.authService.setUserFromJwtToken(data.token, data.refreshToken, false)),
        mergeMap(() => this.adminService.getJwtSettings()),
        tap(jwtSettings => this.processJwtSettings(jwtSettings))
      );
    }
    return of(null);
  })).subscribe(() => {});
}
```

**Lines 179-190**: Confirmation dialog (NOT password authentication)
```typescript
private confirmChangeJWTSettings(): Observable<boolean> {
  if (this.jwtSecuritySettingsFormGroup.get('tokenIssuer').value !== (this.jwtSettings?.tokenIssuer || '') ||
    this.jwtSecuritySettingsFormGroup.get('tokenSigningKey').value !== (this.jwtSettings?.tokenSigningKey || '')) {
    return this.dialogService.confirm(
      this.translate.instant('admin.jwt.info-header'),
      `<div style="max-width: 640px">${this.translate.instant('admin.jwt.info-message')}</div>`,
      this.translate.instant('action.discard-changes'),
      this.translate.instant('action.confirm')
    );
  }
  return of(true);
}
```

**Current Behavior**:
- `save()` method: Requires password authentication for regular Admin users when saving password policy settings
- `saveJwtSettings()` method: Does NOT require password authentication
- `confirmChangeJWTSettings()`: Only shows a confirmation dialog if token issuer or signing key changes, but NOT for auto logout duration changes
- Auto logout duration changes can be saved without any password verification

#### 2. Security Settings HTML Template
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/admin/security-settings.component.html`

**Lines 228-247**: Auto logout duration field (Refresh Token Expiration Time)
```html
<mat-form-field fxFlex class="mat-block">
  <mat-label translate>admin.jwt.refresh-expiration-time</mat-label>
  <input matInput type="number" required
         formControlName="refreshTokenExpTime"
         step="1"
         min="0"/>
  <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('refreshTokenExpTime').hasError('required')">
    {{ 'admin.jwt.refresh-expiration-time-required' | translate }}
  </mat-error>
  <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('refreshTokenExpTime').hasError('pattern')">
    {{ 'admin.jwt.refresh-expiration-time-pattern' | translate }}
  </mat-error>
  <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('refreshTokenExpTime').hasError('min')">
    {{ 'admin.jwt.refresh-expiration-time-min' | translate }}
  </mat-error>
  <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('refreshTokenExpTime').hasError('lessToken')">
    {{ 'admin.jwt.refresh-expiration-time-less-token' | translate }}
  </mat-error>
</mat-form-field>
```

**Lines 248-258**: Save button - calls `saveJwtSettings()` without password check
```html
<div fxLayout="row" fxLayoutAlign="end center" fxLayoutGap="8px" class="layout-wrap">
  <button mat-button color="primary"
          [disabled]="jwtSecuritySettingsFormGroup.pristine"
          (click)="discardJwtSetting()"
          type="button">{{'action.undo' | translate}}
  </button>
  <button mat-raised-button color="primary"
          [disabled]="(isLoading$ | async) || jwtSecuritySettingsFormGroup.invalid || !jwtSecuritySettingsFormGroup.dirty"
          type="submit">{{'action.save' | translate}}
  </button>
</div>
```

### Backend Components

#### 1. Admin Controller - Save JWT Settings Endpoint
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/AdminController.java`

**Lines 237-249**: Save JWT Settings endpoint - NO audit logging
```java
@ApiOperation(value = "Update JWT Settings (saveJwtSettings)",
        notes = "Updates the JWT Settings object that contains JWT token policy, etc. The tokenSigningKey field is a Base64 encoded string." + SYSTEM_AUTHORITY_PARAGRAPH)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/jwtSettings", method = RequestMethod.POST)
@ResponseBody
public JwtPair saveJwtSettings(
        @Parameter(description = "A JSON value representing the JWT Settings.")
        @RequestBody JwtSettings jwtSettings) throws ThingsboardException {
    SecurityUser securityUser = getCurrentUser();
    accessControlService.checkPermission(securityUser, Resource.ADMIN_SETTINGS, Operation.WRITE);
    checkNotNull(jwtSettingsService.saveJwtSettings(jwtSettings));
    return tokenFactory.createTokenPair(securityUser);
}
```

**Current Behavior**:
- No audit log entry is created when JWT settings are saved
- No tracking of who changed the auto logout duration or when
- No record of what the previous value was

#### 2. Admin Controller - Save Security Settings Endpoint (for comparison)
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/AdminController.java`

**Lines 175-225**: Save Security Settings endpoint - WITH audit logging
```java
@ApiOperation(value = "Update Security Settings (saveSecuritySettings)",
        notes = "Updates the Security Settings object that contains password policy, etc." + SYSTEM_AUTHORITY_PARAGRAPH)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/securitySettings", method = RequestMethod.POST)
@ResponseBody
public SecuritySettings saveSecuritySettings(
        @Parameter(description = "A JSON value representing the Security Settings.")
        @RequestBody SecuritySettings securitySettings) throws ThingsboardException {
    accessControlService.checkPermission(getCurrentUser(), Resource.ADMIN_SETTINGS, Operation.WRITE);

    try {
        securitySettings = checkNotNull(systemSecurityService.saveSecuritySettings(securitySettings));

        // Publish audit log for successful security settings update
        SecurityUser currentUser = getCurrentUser();
        // Create a simple action data to identify this as security settings update
        Map<String, Object> actionData = new HashMap<>();
        actionData.put("settingsType", "PASSWORD_POLICY");
        actionData.put("entityData", securitySettings);

        auditLogService.logEntityAction(currentUser.getTenantId(),
                currentUser.getCustomerId(),
                currentUser.getId(),
                currentUser.getName(),
                currentUser.getId(),
                currentUser,
                ActionType.UPDATED,
                null,
                actionData);

        return securitySettings;
    } catch (Exception e) {
        // Publish audit log for failed security settings update
        SecurityUser currentUser = getCurrentUser();
        // Create a simple action data to identify this as security settings update
        Map<String, Object> actionData = new HashMap<>();
        actionData.put("settingsType", "PASSWORD_POLICY");
        actionData.put("entityData", securitySettings);

        auditLogService.logEntityAction(currentUser.getTenantId(),
                currentUser.getCustomerId(),
                currentUser.getId(),
                currentUser.getName(),
                currentUser.getId(),
                currentUser,
                ActionType.UPDATED,
                e,
                actionData);
        throw e;
    }
}
```

**Comparison**: Security settings endpoint includes comprehensive audit logging for both success and failure cases, while JWT settings endpoint has NO audit logging.

#### 3. Audit Log Service Interface
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/dao/src/main/java/org/thingsboard/server/dao/audit/AuditLogService.java`

```java
void logEntityAction(TenantId tenantId, CustomerId customerId, UserId userId, String userName,
                    EntityId entityId, Object entity, ActionType actionType,
                    Exception e, Object... additionalInfo);
```

#### 4. Action Types Available
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/common/data/src/main/java/org/thingsboard/server/common/data/audit/ActionType.java`

**Lines 23-62**: Available action types
```java
public enum ActionType {
    ADDED(false, TbMsgType.ENTITY_CREATED),
    DELETED(false, TbMsgType.ENTITY_DELETED),
    UPDATED(false, TbMsgType.ENTITY_UPDATED),  // <- Should use this for JWT settings update
    ATTRIBUTES_UPDATED(false, TbMsgType.ATTRIBUTES_UPDATED),
    // ... other action types
    SMS_SENT(false, null),
    REPORT_GENERATED(false, TbMsgType.REPORT_GENERATED);
}
```

### Locale Strings

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/assets/locale/locale.constant-en_US.json`

**Line 507**: Label for auto logout duration field
```json
"refresh-expiration-time": "Auto logout duration (sec)"
```

---

## Expectation after the Change

### 1. Password Authentication Flow

When an admin changes the auto logout duration (refreshTokenExpTime) and clicks Save:

1. **Password Confirmation Dialog Appears**:
   - System displays relogin dialog requesting password confirmation
   - Dialog should match the pattern used for security settings save
   - Only required for regular Admin users (not Controlytics Admin)

2. **User Enters Password**:
   - Admin enters their current password in the dialog
   - System validates the password

3. **Success Path**:
   - If password is correct: Proceed with saving JWT settings
   - New tokens are generated and returned
   - Settings are persisted to database
   - Audit log entry is created

4. **Failure Path**:
   - If password is incorrect: Show error message
   - Do not save the settings
   - Allow user to retry or cancel
   - Log failed attempt in audit trail

### 2. Audit Trail Recording

For every auto logout duration change (success or failure):

1. **Audit Log Entry Created** with:
   - **Tenant ID**: Current user's tenant
   - **Customer ID**: Current user's customer (if applicable)
   - **User ID**: ID of the user making the change
   - **User Name**: Name of the user making the change
   - **Entity ID**: User ID (as entity being affected)
   - **Entity Type**: USER
   - **Action Type**: UPDATED
   - **Action Data**: JSON containing:
     - `settingsType`: "JWT_SETTINGS" or "AUTO_LOGOUT"
     - `entityData`: Complete JWT settings object showing new values
     - `oldRefreshTokenExpTime`: Previous auto logout duration
     - `newRefreshTokenExpTime`: New auto logout duration
   - **Status**: SUCCESS or FAILURE (with exception details)
   - **Timestamp**: When the change was made

2. **Audit Log Visibility**:
   - Entry appears in Audit Logs page
   - Searchable by user, action type, date range
   - Shows clear description: "Updated JWT settings - Auto logout duration changed from X to Y seconds"
   - Includes full context for compliance and security review

3. **Error Case Logging**:
   - Failed attempts (validation errors, password incorrect, etc.) also logged
   - Error details included in audit entry
   - Helps identify unauthorized access attempts

### 3. User Experience

1. **For Regular Admin Users**:
   - Change auto logout duration value
   - Click Save button
   - **NEW**: Relogin dialog appears requesting password
   - Enter password to confirm
   - Settings saved successfully
   - Confirmation message displayed
   - **NEW**: Can view change in Audit Logs

2. **For Controlytics Admin Users**:
   - Change auto logout duration value
   - Click Save button
   - **NEW**: May skip password authentication (based on role)
   - Settings saved successfully
   - Confirmation message displayed
   - **NEW**: Change logged in Audit Logs

3. **Audit Trail Access**:
   - Navigate to Audit Logs page
   - Filter by action type "UPDATED"
   - Filter by entity type "USER" or search for "JWT Settings"
   - View all auto logout duration changes
   - See who made the change, when, and what values changed

---

## Changes Required

### Frontend Changes

#### 1. Update Security Settings Component TypeScript
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/admin/security-settings.component.ts`

**Modify the `saveJwtSettings()` method** (Lines 141-153) to include password authentication:

```typescript
saveJwtSettings() {
  this.store.pipe(select(selectAuth)).subscribe(auth => {
    const currentUser = auth.userDetails;
    const jwtFormSettings = this.jwtSecuritySettingsFormGroup.value;

    // Check if current user is Admin (not Controlytics Admin)
    if (currentUser.additionalInfo?.role === UserRole.ADMIN) {
      // Show re-authentication dialog for Admin users before saving JWT settings
      this.dialogService.relogin({
        remarksRequired: false,
        intervalRequired: false,
        timeRangeRequired: false,
        userNameInputRequired: false
      } as ReLoginDialogComponentData).subscribe(
        (result) => {
          if (result && result.reloginStatus) {
            // Proceed with saving JWT settings after successful re-authentication
            this.performSaveJwtSettings(jwtFormSettings);
          }
        }
      );
    } else {
      // Skip re-authentication for Controlytics Admin or other roles
      this.performSaveJwtSettings(jwtFormSettings);
    }
  });
}

/**
 * Perform the actual JWT settings save operation
 */
private performSaveJwtSettings(jwtFormSettings: any): void {
  this.confirmChangeJWTSettings().pipe(mergeMap(value => {
    if (value) {
      return this.adminService.saveJwtSettings(jwtFormSettings).pipe(
        tap((data) => this.authService.setUserFromJwtToken(data.token, data.refreshToken, false)),
        mergeMap(() => this.adminService.getJwtSettings()),
        tap(jwtSettings => this.processJwtSettings(jwtSettings))
      );
    }
    return of(null);
  })).subscribe(() => {});
}
```

**Rationale**:
- Follows the same pattern as `save()` method for security settings
- Checks user role to determine if password authentication is required
- Only Admin users need to re-authenticate (not Controlytics Admin)
- Maintains existing confirmation dialog logic for token issuer/signing key changes
- Extracts save logic into separate method for clean separation

### Backend Changes

#### 1. Update Admin Controller - Add Audit Logging to JWT Settings Save
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/AdminController.java`

**Modify the `saveJwtSettings()` method** (Lines 237-249) to add audit logging:

```java
@ApiOperation(value = "Update JWT Settings (saveJwtSettings)",
        notes = "Updates the JWT Settings object that contains JWT token policy, etc. The tokenSigningKey field is a Base64 encoded string." + SYSTEM_AUTHORITY_PARAGRAPH)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/jwtSettings", method = RequestMethod.POST)
@ResponseBody
public JwtPair saveJwtSettings(
        @Parameter(description = "A JSON value representing the JWT Settings.")
        @RequestBody JwtSettings jwtSettings) throws ThingsboardException {
    SecurityUser securityUser = getCurrentUser();
    accessControlService.checkPermission(securityUser, Resource.ADMIN_SETTINGS, Operation.WRITE);

    try {
        // Get old settings for audit comparison
        JwtSettings oldSettings = jwtSettingsService.getJwtSettings();

        // Save new settings
        checkNotNull(jwtSettingsService.saveJwtSettings(jwtSettings));

        // Create token pair for response
        JwtPair tokenPair = tokenFactory.createTokenPair(securityUser);

        // Publish audit log for successful JWT settings update
        Map<String, Object> actionData = new HashMap<>();
        actionData.put("settingsType", "JWT_SETTINGS");
        actionData.put("oldRefreshTokenExpTime", oldSettings != null ? oldSettings.getRefreshTokenExpTime() : null);
        actionData.put("newRefreshTokenExpTime", jwtSettings.getRefreshTokenExpTime());
        actionData.put("oldTokenExpirationTime", oldSettings != null ? oldSettings.getTokenExpirationTime() : null);
        actionData.put("newTokenExpirationTime", jwtSettings.getTokenExpirationTime());
        actionData.put("entityData", jwtSettings);

        auditLogService.logEntityAction(securityUser.getTenantId(),
                securityUser.getCustomerId(),
                securityUser.getId(),
                securityUser.getName(),
                securityUser.getId(),
                securityUser,
                ActionType.UPDATED,
                null,
                actionData);

        return tokenPair;
    } catch (Exception e) {
        // Publish audit log for failed JWT settings update
        Map<String, Object> actionData = new HashMap<>();
        actionData.put("settingsType", "JWT_SETTINGS");
        actionData.put("attemptedRefreshTokenExpTime", jwtSettings.getRefreshTokenExpTime());
        actionData.put("attemptedTokenExpirationTime", jwtSettings.getTokenExpirationTime());
        actionData.put("entityData", jwtSettings);

        auditLogService.logEntityAction(securityUser.getTenantId(),
                securityUser.getCustomerId(),
                securityUser.getId(),
                securityUser.getName(),
                securityUser.getId(),
                securityUser,
                ActionType.UPDATED,
                e,
                actionData);
        throw e;
    }
}
```

**Rationale**:
- Follows the exact same pattern as `saveSecuritySettings()` method
- Captures old values for comparison in audit logs
- Logs both success and failure cases
- Uses `ActionType.UPDATED` as this is a configuration update
- Includes comprehensive action data:
  - Setting type identifier ("JWT_SETTINGS")
  - Old and new values for auto logout duration
  - Old and new values for token expiration time
  - Complete settings object for full context
- Exception details are captured in failure case

### Optional Enhancement: Improve Audit Log Display

#### Update Audit Log Label/Description
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/assets/locale/locale.constant-en_US.json`

**Add new locale strings** for better audit log descriptions:

```json
{
  "audit-log": {
    "action-type": {
      "jwt-settings-updated": "JWT Settings Updated",
      "auto-logout-changed": "Auto Logout Duration Changed"
    },
    "action-data": {
      "jwt-settings": "JWT Settings",
      "auto-logout-duration": "Auto Logout Duration",
      "old-value": "Previous Value",
      "new-value": "New Value"
    }
  }
}
```

**Note**: This is optional and depends on how audit logs format their display messages.

---

## Implementation Summary

### Changes Required:

1. **Frontend** (`security-settings.component.ts`):
   - Add password authentication check to `saveJwtSettings()` method
   - Extract save logic to `performSaveJwtSettings()` private method
   - Follow same pattern as existing `save()` method for security settings

2. **Backend** (`AdminController.java`):
   - Add audit logging to `saveJwtSettings()` endpoint
   - Capture old settings before save for comparison
   - Log both success and failure cases with comprehensive action data
   - Follow same pattern as existing `saveSecuritySettings()` method

3. **Optional**: Update locale strings for better audit log display

### Pattern to Follow:

Both changes follow existing patterns in the codebase:
- Frontend: Same pattern as `save()` method (lines 108-132)
- Backend: Same pattern as `saveSecuritySettings()` method (lines 175-225)

### Testing Considerations:

1. **Password Authentication**:
   - Test with regular Admin user - should show password dialog
   - Test with Controlytics Admin user - may skip password dialog (based on role check)
   - Test incorrect password - should not save and show error
   - Test correct password - should save successfully

2. **Audit Trail**:
   - Verify audit log entry created on successful save
   - Verify audit log entry created on failed save
   - Verify old and new values captured correctly
   - Verify audit log visible in Audit Logs page
   - Verify searchable by user, date, action type

3. **Integration**:
   - Verify JWT settings still save correctly after changes
   - Verify new tokens returned after successful save
   - Verify tokens properly updated in session
   - Verify no regression in existing functionality

### Security Benefits:

1. **Accountability**: Every auto logout change is tracked with user identity and timestamp
2. **Verification**: Admin must confirm identity before making sensitive timeout changes
3. **Compliance**: Full audit trail for security compliance requirements
4. **Forensics**: Can trace unauthorized or accidental changes
5. **Consistency**: Same security controls applied to both password policy and JWT settings
