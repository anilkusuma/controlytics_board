# Requirement #2: JWT Token Settings Visibility for Controlytics Admin

## Requirement

JWT Token settings should be available for Controlytics Admin (developers/SYS_ADMIN). Currently, only the auto logout setting is visible to admin users. For Controlytics Admin (developers), other timeout settings should be visible, as there are some restrictions like "auto logout cannot be less than refresh token", which causes issues when configuring this time and there is no option to change the parameter as it is not visible.

### Current Issues:
1. Only auto logout (refresh token expiration time) is visible to regular Admin users
2. Token expiration time, token issuer, and token signing key are hidden
3. Validation rule exists: `tokenExpirationTime < refreshTokenExpTime` (access token must be less than refresh token)
4. When admins try to configure auto logout, they may face validation errors because they cannot see or adjust the token expiration time
5. Controlytics Admin users need access to all JWT settings to properly configure the system

## Current Implementation as per Code

### Frontend Components

#### 1. Security Settings HTML Template
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/admin/security-settings.component.html`

**Lines 193-227**: JWT settings form fields with visibility controls

```html
<!-- HIDDEN: Token Issuer -->
<mat-form-field fxFlex class="mat-block" style="display: none !important; visibility: hidden;">
  <mat-label translate>admin.jwt.issuer-name</mat-label>
  <input matInput formControlName="tokenIssuer"/>
</mat-form-field>

<!-- HIDDEN: Token Signing Key -->
<mat-form-field fxFlex class="mat-block" style="display: none !important; visibility: hidden;">
  <mat-label translate>admin.jwt.signings-key</mat-label>
  <input matInput formControlName="tokenSigningKey"/>
  <button type="button" matSuffix mat-button (click)="generateSigningKey()" color="primary">
    {{ 'admin.jwt.generate-key' | translate }}
  </button>
</mat-form-field>

<!-- HIDDEN: Token Expiration Time (Access Token) -->
<mat-form-field fxFlex class="mat-block" style="display: none;">
  <mat-label translate>admin.jwt.expiration-time</mat-label>
  <input matInput type="number" required formControlName="tokenExpirationTime" step="1" min="0"/>
  <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('tokenExpirationTime').hasError('required')">
    {{ 'admin.jwt.expiration-time-required' | translate }}
  </mat-error>
  <!-- ... additional validation errors ... -->
</mat-form-field>

<!-- VISIBLE: Refresh Token Expiration Time (Auto Logout) -->
<mat-form-field fxFlex class="mat-block">
  <mat-label translate>admin.jwt.refresh-expiration-time</mat-label>
  <input matInput type="number" required formControlName="refreshTokenExpTime" step="1" min="0"/>
  <!-- ... validation errors ... -->
</mat-form-field>
```

**Current Visibility**:
- `tokenIssuer`: `display: none !important; visibility: hidden;`
- `tokenSigningKey`: `display: none !important; visibility: hidden;`
- `tokenExpirationTime` (access token): `display: none;`
- `refreshTokenExpTime` (auto logout): **VISIBLE** to all users

#### 2. Security Settings TypeScript Component
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/admin/security-settings.component.ts`

**Lines 99-106**: JWT form initialization
```typescript
buildJwtSecuritySettingsForm() {
  this.jwtSecuritySettingsFormGroup = this.fb.group({
    tokenIssuer: ['thingsboardDefaultIssuer'],
    tokenSigningKey: ['thingsboardDefaultSigningKey'],
    tokenExpirationTime: [9000],
    refreshTokenExpTime: [0, [Validators.required, Validators.pattern('[0-9]*'), Validators.min(900)]]
  });
}
```

**Lines 109-132**: User role check for re-authentication
```typescript
save(): void {
  this.store.pipe(select(selectAuth)).subscribe(auth => {
    const currentUser = auth.userDetails;
    // Check if current user is Admin (not Controlytics Admin)
    if (currentUser.additionalInfo?.role === UserRole.ADMIN) {
      // Show re-authentication dialog for Admin users
      this.dialogService.relogin({...}).subscribe((result) => {
        if (result && result.reloginStatus) {
          this.performSaveSecuritySettings();
        }
      });
    } else {
      // Skip re-authentication for Controlytics Admin or other roles
      this.performSaveSecuritySettings();
    }
  });
}
```

**Lines 210-223**: Validation function (unused in current implementation)
```typescript
private refreshTokenTimeGreatTokenTime(formGroup: UntypedFormGroup): { [key: string]: boolean } | null {
  if (formGroup) {
    const tokenTime = formGroup.value.tokenExpirationTime;
    const refreshTokenTime = formGroup.value.refreshTokenExpTime;
    if (tokenTime >= refreshTokenTime) {
      if (formGroup.get('refreshTokenExpTime').untouched) {
        formGroup.get('refreshTokenExpTime').markAsTouched();
      }
      formGroup.get('refreshTokenExpTime').setErrors({lessToken: true});
      return {lessToken: true};
    }
  }
  return null;
}
```

**Note**: The validation function `refreshTokenTimeGreatTokenTime` exists but is **NOT** called anywhere in the component, so frontend validation for token time relationship is not enforced.

### Backend Validation

#### 1. JWT Settings Validator
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/service/security/auth/jwt/settings/DefaultJwtSettingsValidator.java`

**Lines 38-50**: Server-side validation rules
```java
@Override
public void validate(JwtSettings jwtSettings) {
    if (StringUtils.isEmpty(jwtSettings.getTokenIssuer())) {
        throw new DataValidationException("JWT token issuer should be specified!");
    }
    if (Optional.ofNullable(jwtSettings.getRefreshTokenExpTime()).orElse(0) < TimeUnit.MINUTES.toSeconds(15)) {
        throw new DataValidationException("JWT refresh token expiration time should be at least 15 minutes!");
    }
    if (Optional.ofNullable(jwtSettings.getTokenExpirationTime()).orElse(0) < TimeUnit.MINUTES.toSeconds(1)) {
        throw new DataValidationException("JWT token expiration time should be at least 1 minute!");
    }
    if (jwtSettings.getTokenExpirationTime() >= jwtSettings.getRefreshTokenExpTime()) {
        throw new DataValidationException("JWT token expiration time should greater than JWT refresh token expiration time!");
    }
    // ... additional validation for signing key ...
}
```

**Validation Rules**:
1. Token issuer must be specified
2. Refresh token expiration time (auto logout) >= 900 seconds (15 minutes)
3. Token expiration time (access token) >= 60 seconds (1 minute)
4. **Token expiration time MUST BE LESS than refresh token expiration time**
5. Token signing key must be valid Base64 with at least 512 bits

#### 2. Admin Controller
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/AdminController.java`

**Lines 227-235**: Get JWT Settings endpoint
```java
@ApiOperation(value = "Get the JWT Settings object (getJwtSettings)", ...)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/jwtSettings", method = RequestMethod.GET)
@ResponseBody
public JwtSettings getJwtSettings() throws ThingsboardException {
    accessControlService.checkPermission(getCurrentUser(), Resource.ADMIN_SETTINGS, Operation.READ);
    return checkNotNull(jwtSettingsService.getJwtSettings());
}
```

**Lines 237-249**: Save JWT Settings endpoint
```java
@ApiOperation(value = "Update JWT Settings (saveJwtSettings)", ...)
@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
@RequestMapping(value = "/jwtSettings", method = RequestMethod.POST)
@ResponseBody
public JwtPair saveJwtSettings(@RequestBody JwtSettings jwtSettings) throws ThingsboardException {
    SecurityUser securityUser = getCurrentUser();
    accessControlService.checkPermission(securityUser, Resource.ADMIN_SETTINGS, Operation.WRITE);
    checkNotNull(jwtSettingsService.saveJwtSettings(jwtSettings));
    return tokenFactory.createTokenPair(securityUser);
}
```

**Backend Authorization**: Both endpoints allow `SYS_ADMIN` and `TENANT_ADMIN` authorities.

### User Roles

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/shared/models/user.model.ts`

**Lines 35-41**: User role definitions
```typescript
export enum UserRole {
  ADMIN = 'admin',
  OPERATOR = 'operator',
  MAINTENANCE = 'maintenance',
  SUPERVISOR = 'supervisor',
  CONTROLYTICS_ADMIN = 'controlytics_admin',
}
```

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/shared/models/authority.enum.ts`

**Lines 17-24**: Authority enum (used for Spring Security)
```typescript
export enum Authority {
  SYS_ADMIN = 'SYS_ADMIN',
  TENANT_ADMIN = 'TENANT_ADMIN',
  CUSTOMER_USER = 'CUSTOMER_USER',
  REFRESH_TOKEN = 'REFRESH_TOKEN',
  ANONYMOUS = 'ANONYMOUS',
  PRE_VERIFICATION_TOKEN = 'PRE_VERIFICATION_TOKEN'
}
```

**Note**: There are two separate concepts:
1. **UserRole** (stored in `user.additionalInfo.role`): Used for application-level role checks (admin, controlytics_admin, operator, etc.)
2. **Authority** (Spring Security): Used for backend authorization (SYS_ADMIN, TENANT_ADMIN, CUSTOMER_USER)

### Locale Strings

**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/assets/locale/locale.constant-en_US.json`

**Lines 495-515**: JWT settings translations
```json
"jwt": {
  "security-settings": "Auto logout settings",
  "issuer-name": "Issuer name",
  "signings-key": "Signing key",
  "expiration-time": "Token expiration time (sec)",
  "refresh-expiration-time": "Auto logout duration (sec)",
  ...
  "refresh-expiration-time-less-token": "Refresh token time must be greater token time."
}
```

## Expectation after the Change

1. **For Regular Admin Users** (`UserRole.ADMIN`):
   - Continue to see only the "Auto logout duration (sec)" field (refreshTokenExpTime)
   - Hidden fields remain hidden: tokenIssuer, tokenSigningKey, tokenExpirationTime

2. **For Controlytics Admin Users** (`UserRole.CONTROLYTICS_ADMIN`):
   - See ALL JWT token settings:
     - Token Issuer (tokenIssuer)
     - Token Signing Key (tokenSigningKey) with "Generate Key" button
     - Token expiration time (tokenExpirationTime) - Access token timeout
     - Auto logout duration (refreshTokenExpTime) - Refresh token timeout
   - Can configure all parameters to avoid validation conflicts
   - Have full control over JWT token policy

3. **Validation Behavior**:
   - Backend validation rules remain unchanged
   - Frontend should show validation errors clearly:
     - "Token expiration time must be less than auto logout duration"
     - "Auto logout duration must be at least 15 minutes (900 seconds)"
     - "Token expiration time must be at least 1 minute (60 seconds)"

4. **User Experience**:
   - Controlytics Admin can see the relationship between fields
   - Can adjust token expiration time when changing auto logout duration
   - Avoid unexpected validation errors
   - Better control for system configuration

## Changes Required

### Frontend Changes

#### 1. Update Security Settings Component TypeScript
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/admin/security-settings.component.ts`

**Changes needed**:
```typescript
// Add a property to track if user is Controlytics Admin
isControlyticsAdmin: boolean = false;

constructor(...) {
  super(store);

  // Check user role
  this.store.pipe(select(selectAuth)).subscribe(auth => {
    const currentUser = auth.userDetails;
    this.isControlyticsAdmin = currentUser.additionalInfo?.role === UserRole.CONTROLYTICS_ADMIN;
  });

  this.buildSecuritySettingsForm();
  this.buildJwtSecuritySettingsForm();
  // ... rest of initialization
}

// Update JWT form builder to add proper validators
buildJwtSecuritySettingsForm() {
  this.jwtSecuritySettingsFormGroup = this.fb.group({
    tokenIssuer: ['thingsboardDefaultIssuer', [Validators.required]],
    tokenSigningKey: ['thingsboardDefaultSigningKey', [Validators.required, this.base64Format.bind(this)]],
    tokenExpirationTime: [9000, [Validators.required, Validators.pattern('[0-9]*'), Validators.min(60)]],
    refreshTokenExpTime: [0, [Validators.required, Validators.pattern('[0-9]*'), Validators.min(900)]]
  }, { validators: this.refreshTokenTimeGreatTokenTime.bind(this) });
}

// Update the existing validation function to be used as form validator
private refreshTokenTimeGreatTokenTime(formGroup: AbstractControl): ValidationErrors | null {
  if (formGroup instanceof UntypedFormGroup) {
    const tokenTime = formGroup.value.tokenExpirationTime;
    const refreshTokenTime = formGroup.value.refreshTokenExpTime;
    if (tokenTime && refreshTokenTime && tokenTime >= refreshTokenTime) {
      return { tokenExpirationGreaterThanRefresh: true };
    }
  }
  return null;
}
```

#### 2. Update Security Settings HTML Template
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/admin/security-settings.component.html`

**Changes needed**:
```html
<!-- Replace lines 193-227 with role-based visibility -->

<div fxLayout="row" fxLayout.xs="column" fxLayoutGap="8px">
  <!-- Token Issuer - Only visible for Controlytics Admin -->
  <mat-form-field fxFlex class="mat-block" *ngIf="isControlyticsAdmin" [style.display]="isControlyticsAdmin ? 'block' : 'none'">
    <mat-label translate>admin.jwt.issuer-name</mat-label>
    <input matInput formControlName="tokenIssuer"/>
    <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('tokenIssuer').hasError('required')">
      {{ 'admin.jwt.issuer-name-required' | translate }}
    </mat-error>
  </mat-form-field>

  <!-- Token Signing Key - Only visible for Controlytics Admin -->
  <mat-form-field fxFlex class="mat-block" *ngIf="isControlyticsAdmin" [style.display]="isControlyticsAdmin ? 'block' : 'none'">
    <mat-label translate>admin.jwt.signings-key</mat-label>
    <input matInput formControlName="tokenSigningKey"/>
    <button type="button"
            style="line-height: 32px"
            matSuffix
            mat-button
            (click)="generateSigningKey()"
            color="primary">
      {{ 'admin.jwt.generate-key' | translate }}
    </button>
    <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('tokenSigningKey').hasError('required')">
      {{ 'admin.jwt.signings-key-required' | translate }}
    </mat-error>
    <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('tokenSigningKey').hasError('minLength')">
      {{ 'admin.jwt.signings-key-min-length' | translate }}
    </mat-error>
    <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('tokenSigningKey').hasError('base64')">
      {{ 'admin.jwt.signings-key-base64' | translate }}
    </mat-error>
  </mat-form-field>
</div>

<div fxLayout="row" fxLayout.xs="column" fxLayoutGap="8px">
  <!-- Token Expiration Time - Only visible for Controlytics Admin -->
  <mat-form-field fxFlex class="mat-block" *ngIf="isControlyticsAdmin" [style.display]="isControlyticsAdmin ? 'block' : 'none'">
    <mat-label translate>admin.jwt.expiration-time</mat-label>
    <input matInput type="number" required
           formControlName="tokenExpirationTime"
           step="1"
           min="60"/>
    <mat-hint>{{ 'admin.jwt.expiration-time-hint' | translate }}</mat-hint>
    <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('tokenExpirationTime').hasError('required')">
      {{ 'admin.jwt.expiration-time-required' | translate }}
    </mat-error>
    <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('tokenExpirationTime').hasError('pattern')">
      {{ 'admin.jwt.expiration-time-pattern' | translate }}
    </mat-error>
    <mat-error *ngIf="jwtSecuritySettingsFormGroup.get('tokenExpirationTime').hasError('min')">
      {{ 'admin.jwt.expiration-time-min' | translate }}
    </mat-error>
  </mat-form-field>

  <!-- Refresh Token Expiration Time - Visible for all users -->
  <mat-form-field fxFlex class="mat-block">
    <mat-label translate>admin.jwt.refresh-expiration-time</mat-label>
    <input matInput type="number" required
           formControlName="refreshTokenExpTime"
           step="1"
           min="900"/>
    <mat-hint *ngIf="isControlyticsAdmin">{{ 'admin.jwt.refresh-expiration-time-hint' | translate }}</mat-hint>
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
</div>

<!-- Form-level validation error for token time relationship -->
<div *ngIf="jwtSecuritySettingsFormGroup.hasError('tokenExpirationGreaterThanRefresh') && jwtSecuritySettingsFormGroup.touched"
     class="mat-error" style="margin-top: -8px; margin-bottom: 16px;">
  {{ 'admin.jwt.token-expiration-greater-than-refresh-error' | translate }}
</div>
```

#### 3. Update Locale Strings
**File**: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/assets/locale/locale.constant-en_US.json`

**Add new translations** (around line 515):
```json
"jwt": {
  "security-settings": "Auto logout settings",
  "issuer-name": "Issuer name",
  "issuer-name-required": "Issuer name is required.",
  "signings-key": "Signing key",
  "signings-key-hint": "Base64 encoded string representing at least 512 bits of data.",
  "signings-key-required": "Signing key is required.",
  "signings-key-min-length": "Signing key must be at least 512 bits of data.",
  "signings-key-base64": "Signing key must be base64 format.",
  "expiration-time": "Token expiration time (sec)",
  "expiration-time-hint": "Access token validity period in seconds. Must be less than auto logout duration.",
  "expiration-time-required": "Token expiration time is required.",
  "expiration-time-pattern": "Token expiration time must be a positive integer.",
  "expiration-time-min": "Minimum time is 60 seconds (1 minute).",
  "refresh-expiration-time": "Auto logout duration (sec)",
  "refresh-expiration-time-hint": "Refresh token validity period in seconds. Must be greater than token expiration time.",
  "refresh-expiration-time-required": "Refresh token expiration time is required.",
  "refresh-expiration-time-pattern": "Refresh token expiration time must be a positive integer.",
  "refresh-expiration-time-min": "Minimum time is 900 seconds (15 minutes).",
  "refresh-expiration-time-less-token": "Refresh token time must be greater than token time.",
  "token-expiration-greater-than-refresh-error": "Token expiration time must be less than auto logout duration (refresh token time).",
  "generate-key": "Generate key",
  "info-header": "All users will be required to re-login",
  "info-message": "Change of the JWT Signing Key will cause all issued tokens to be invalid. All users will need to re-login. This will also affect scripts that use Rest API/Websockets."
}
```

### Backend Changes

**No backend changes required**. The backend already:
1. Returns all JWT settings fields via `/api/admin/jwtSettings` GET endpoint
2. Accepts all JWT settings fields via `/api/admin/jwtSettings` POST endpoint
3. Has proper validation in `DefaultJwtSettingsValidator`
4. Authorizes both `SYS_ADMIN` and `TENANT_ADMIN` authorities

The frontend was hiding these fields using CSS, so the backend already supports the full functionality.

## Multiple Options

### Option 1: Role-Based Visibility (RECOMMENDED)

**Approach**: Use Angular `*ngIf` directive to conditionally show/hide fields based on `UserRole.CONTROLYTICS_ADMIN`.

**Pros**:
- Clean separation between admin and controlytics admin views
- DOM elements are not rendered for regular admins (better security)
- Simple to implement and maintain
- Clear user experience - users only see what they can configure
- Follows existing pattern in codebase (line 112 checks UserRole.ADMIN)

**Cons**:
- Need to track user role in component state
- Slightly more complex HTML template

**Implementation**:
- Add `isControlyticsAdmin` property to component
- Use `*ngIf="isControlyticsAdmin"` on form fields
- Keep existing inline styles as fallback: `[style.display]="isControlyticsAdmin ? 'block' : 'none'"`

### Option 2: Authority-Based Visibility

**Approach**: Use Spring Security `Authority` enum instead of `UserRole`.

**Pros**:
- Aligns with backend authorization model
- Consistent with backend endpoint security (`@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")`)
- Could be more secure if authority hierarchy is established

**Cons**:
- Current backend allows both SYS_ADMIN and TENANT_ADMIN
- May show fields to TENANT_ADMIN users who shouldn't have access
- Requires understanding of authority vs role distinction
- More complex to implement correctly

**Not Recommended**: Because backend endpoints don't distinguish between SYS_ADMIN and TENANT_ADMIN, and the requirement specifically mentions "Controlytics Admin (developer)" which maps to `UserRole.CONTROLYTICS_ADMIN`.

### Option 3: Permission-Based Visibility

**Approach**: Create a new permission/feature flag for JWT advanced settings.

**Pros**:
- Most flexible - can be toggled without code changes
- Could be configured per user/role via database
- Better for future extensibility

**Cons**:
- Significant development effort
- Requires backend changes (new permission system)
- Over-engineered for current requirement
- Adds complexity to system

**Not Recommended**: Overkill for this requirement; existing UserRole mechanism is sufficient.

### Option 4: CSS Class Toggle

**Approach**: Keep existing inline styles, toggle a CSS class based on user role.

**Pros**:
- Minimal code changes
- Quick to implement

**Cons**:
- DOM elements remain in HTML (less secure)
- Client could potentially inspect and modify hidden fields
- Not a clean approach
- Fields still participate in form validation even when hidden

**Not Recommended**: Less secure and less maintainable than Option 1.

## Final Recommendation

**Recommended Approach**: **Option 1 - Role-Based Visibility**

**Reasoning**:
1. **Aligns with existing code patterns**: The codebase already checks `UserRole.ADMIN` on line 112 of security-settings.component.ts
2. **Security**: DOM elements are not rendered for non-controlytics admin users
3. **Clean implementation**: Uses Angular best practices with `*ngIf` directive
4. **Maintainability**: Easy to understand and modify in the future
5. **User experience**: Clear distinction between admin and controlytics admin capabilities
6. **No backend changes needed**: Backend already supports all functionality

**Additional Enhancements**:
1. **Enable form-level validation**: Call `refreshTokenTimeGreatTokenTime` as a form validator to show clear error messages
2. **Add helpful hints**: Include `mat-hint` directives to explain the relationship between token times
3. **Improve error messages**: Add new locale string for form-level validation error
4. **Keep fail-safe**: Maintain inline style as backup: `[style.display]="isControlyticsAdmin ? 'block' : 'none'"`

**Implementation Priority**:
1. Update TypeScript component (add `isControlyticsAdmin` property, update form builder)
2. Update HTML template (add `*ngIf` directives, improve validation display)
3. Update locale strings (add new hints and error messages)
4. Test with both Admin and Controlytics Admin users
5. Verify validation rules work correctly for Controlytics Admin

**Testing Scenarios**:
1. Regular Admin user should only see "Auto logout duration" field
2. Controlytics Admin user should see all 4 JWT fields
3. Validation error should appear when tokenExpirationTime >= refreshTokenExpTime
4. Save should work correctly for Controlytics Admin with all fields
5. Regular Admin save should continue to work with only refresh token field
