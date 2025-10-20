# Requirement #12: Password Expiry Redirect

## Requirement
Once a user's password is expired, the system should automatically redirect the user to the Password Reset Page to force them to change their password. Currently, this redirect is not happening correctly.

## Current Implementation as per Code

### Backend Password Expiry Check
**File:** `/application/src/main/java/org/thingsboard/server/service/security/system/DefaultSystemSecurityService.java`

Lines 162-170: The system checks if password has expired during authentication
```java
SecuritySettings securitySettings = self.getSecuritySettings();
if (isPositiveInteger(securitySettings.getPasswordPolicy().getPasswordExpirationPeriodDays())
        && !(user.isSystemAdmin())) {
    if ((userCredentials.getCreatedTime()
            + TimeUnit.DAYS.toMillis(securitySettings.getPasswordPolicy().getPasswordExpirationPeriodDays()))
            < System.currentTimeMillis()) {
        userCredentials = userService.requestExpiredPasswordReset(tenantId, userCredentials.getId());
        throw new UserPasswordExpiredException("User password expired!", userCredentials.getResetToken());
    }
}
```

### Backend Exception Handling
**File:** `/application/src/main/java/org/thingsboard/server/exception/ThingsboardErrorResponseHandler.java`

Lines 214-216: Exception handler catches password expiry and sends response with resetToken
```java
} else if (authenticationException instanceof UserPasswordExpiredException expiredException) {
    String resetToken = expiredException.getResetToken();
    JacksonUtil.writeValue(response.getWriter(), ThingsboardCredentialsExpiredResponse.of(expiredException.getMessage(), resetToken));
```

**File:** `/application/src/main/java/org/thingsboard/server/exception/ThingsboardCredentialsExpiredResponse.java`

Lines 27-34: Response object includes resetToken
```java
protected ThingsboardCredentialsExpiredResponse(String message, String resetToken) {
    super(message, ThingsboardErrorCode.CREDENTIALS_EXPIRED, HttpStatus.UNAUTHORIZED);
    this.resetToken = resetToken;
}
```

### Frontend Constants
**File:** `/ui-ngx/src/app/shared/models/constants.ts`

Lines 22-36: Error codes defined
```typescript
serverErrorCode: {
    general: 2,
    authentication: 10,
    jwtTokenExpired: 11,
    tenantTrialExpired: 12,
    credentialsExpired: 15,  // <-- Password expiry error code
    permissionDenied: 20,
    invalidArguments: 30,
    badRequestParams: 31,
    itemNotFound: 32,
    tooManyRequests: 33,
    tooManyUpdates: 34,
    resetPasswordRequired: 41,
    createPasswordRequired: 42,
    passwordViolation: 45
}
```

### Frontend Login Component
**File:** `/ui-ngx/src/app/modules/login/pages/login/login.component.ts`

Lines 68-76: Login error handling checks for credentialsExpired error code
```typescript
(error: HttpErrorResponse) => {
  if (error && error.error && error.error.errorCode) {
    if (error.error.errorCode === Constants.serverErrorCode.credentialsExpired) {
      this.router.navigateByUrl(`login/resetExpiredPassword?resetToken=${error.error.resetToken}`);
    } else if (error.error.errorCode === Constants.serverErrorCode.passwordViolation) {
      this.passwordViolation = true;
    }
  }
}
```

### Frontend Auth Service
**File:** `/ui-ngx/src/app/core/auth/auth.service.ts`

Lines 122-152: Login method has INCOMPLETE redirect logic
```typescript
public login(loginRequest: LoginRequest): Observable<LoginResponse> {
  return this.http.post<LoginResponse>('/api/auth/login', loginRequest, {
    ...defaultHttpOptions(),
    observe: 'response'
  }).pipe(map((response: HttpResponse<LoginResponse>)=> {
      if (response.body && response.body.token && response.body.refreshToken) {
        this.setUserFromJwtToken(response.body.token, response.body.refreshToken, true);
        if (response.body.scope === Authority.PRE_VERIFICATION_TOKEN) {
          this.router.navigateByUrl(`login/mfa`);
        }
      }
      return response.body;
    }),
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        console.log(error);
        const redirectUrl = error?.error?.resetToken; // WRONG: Uses resetToken as URL
        if (redirectUrl) {
          console.log('Redirecting to:', redirectUrl);
          window.location.href = redirectUrl; // WRONG: redirectUrl is a token, not a URL
          return of({
            token: '',
            refreshToken: '',
            scope: Authority.TENANT_ADMIN
          } as LoginResponse);
        }
      }
    })
  );
}
```

### Frontend HTTP Interceptor
**File:** `/ui-ngx/src/app/core/interceptors/global-http-interceptor.ts`

Lines 111-115: Interceptor has INCORRECT condition logic
```typescript
if (errorCode !== Constants.serverErrorCode.credentialsExpired
    || errorCode !== Constants.serverErrorCode.resetPasswordRequired
    || errorCode !== Constants.serverErrorCode.createPasswordRequired) {
  unhandled = true;
}
```
**Problem:** Using OR (||) instead of AND (&&) causes ALL errors to be treated as unhandled because at least one condition will always be true.

### Frontend Routing
**File:** `/ui-ngx/src/app/modules/login/login-routing.module.ts`

Lines 57-65: Route exists for expired password reset
```typescript
{
  path: 'login/resetExpiredPassword',
  component: ResetPasswordComponent,
  data: {
    title: 'login.reset-password',
    module: 'public',
    expiredPassword: true
  },
  canActivate: [AuthGuard]
}
```

## Root Cause Analysis

### Issue 1: Incorrect Logic in HTTP Interceptor (CRITICAL)
**File:** `/ui-ngx/src/app/core/interceptors/global-http-interceptor.ts` (Line 111)

The condition uses OR (||) instead of AND (&&):
```typescript
if (errorCode !== Constants.serverErrorCode.credentialsExpired
    || errorCode !== Constants.serverErrorCode.resetPasswordRequired
    || errorCode !== Constants.serverErrorCode.createPasswordRequired) {
```

This causes the `credentialsExpired` error to be marked as `unhandled = true` because:
- When errorCode = 15 (credentialsExpired):
  - errorCode !== 15 = false
  - errorCode !== 41 = true  <-- This is TRUE
  - errorCode !== 42 = true  <-- This is TRUE
  - Result: false || true || true = **true** (unhandled = true)

This means the error gets logged and shown as a generic error message instead of being passed through to the login component for proper handling.

### Issue 2: Incorrect Redirect Logic in Auth Service (CRITICAL)
**File:** `/ui-ngx/src/app/core/auth/auth.service.ts` (Lines 135-147)

The auth service attempts to redirect but uses the resetToken as a URL:
```typescript
const redirectUrl = error?.error?.resetToken; // This is a TOKEN, not a URL!
if (redirectUrl) {
  window.location.href = redirectUrl; // Tries to navigate to a token string
```

The resetToken is a UUID string (e.g., "abc123-def456-..."), not a URL. This causes:
1. The browser tries to navigate to an invalid URL
2. Navigation fails silently or shows browser error
3. User remains stuck on login page

### Issue 3: Conflict Between Login Component and Auth Service
The login component (login.component.ts) has CORRECT redirect logic:
```typescript
if (error.error.errorCode === Constants.serverErrorCode.credentialsExpired) {
  this.router.navigateByUrl(`login/resetExpiredPassword?resetToken=${error.error.resetToken}`);
}
```

But the auth service (auth.service.ts) intercepts the error FIRST and prevents it from reaching the login component because it returns `of({...})` instead of re-throwing the error.

## Expectation after the Change

### Expected Flow:
1. User attempts to login with expired password
2. Backend validates credentials and detects password expiry
3. Backend throws `UserPasswordExpiredException` with resetToken
4. Exception handler returns HTTP 401 with:
   - errorCode: 15 (CREDENTIALS_EXPIRED)
   - resetToken: "uuid-string"
   - message: "User password expired!"
5. Frontend HTTP interceptor recognizes errorCode 15 and allows error to pass through (not mark as unhandled)
6. Frontend login component catches error and redirects to: `/login/resetExpiredPassword?resetToken={token}`
7. User sees password reset page with their email pre-filled
8. User enters new password
9. System validates and resets password
10. User is automatically logged in with new password

## Changes Required

### Change 1: Fix HTTP Interceptor Logic
**File:** `/ui-ngx/src/app/core/interceptors/global-http-interceptor.ts`

**Current Code (Line 111-115):**
```typescript
if (errorCode !== Constants.serverErrorCode.credentialsExpired
    || errorCode !== Constants.serverErrorCode.resetPasswordRequired
    || errorCode !== Constants.serverErrorCode.createPasswordRequired) {
  unhandled = true;
}
```

**Corrected Code:**
```typescript
if (errorCode !== Constants.serverErrorCode.credentialsExpired
    && errorCode !== Constants.serverErrorCode.resetPasswordRequired
    && errorCode !== Constants.serverErrorCode.createPasswordRequired) {
  unhandled = true;
}
```

**Explanation:** Change OR (||) to AND (&&) so that credentialsExpired, resetPasswordRequired, and createPasswordRequired errors are NOT marked as unhandled. This allows them to propagate to the login component.

### Change 2: Fix or Remove Auth Service Redirect Logic
**File:** `/ui-ngx/src/app/core/auth/auth.service.ts`

**Option A - Remove the incorrect redirect logic (RECOMMENDED):**

**Current Code (Lines 135-150):**
```typescript
catchError((error: HttpErrorResponse) => {
  if (error.status === 401) {
    console.log(error);
    const redirectUrl = error?.error?.resetToken;
    if (redirectUrl) {
      console.log('Redirecting to:', redirectUrl);
      window.location.href = redirectUrl;
      return of({
        token: '',
        refreshToken: '',
        scope: Authority.TENANT_ADMIN
      } as LoginResponse);
    }
  }
})
```

**Corrected Code:**
```typescript
catchError((error: HttpErrorResponse) => {
  // Re-throw error to allow login component to handle it
  return throwError(() => error);
})
```

**Option B - Fix the redirect logic to construct proper URL:**
```typescript
catchError((error: HttpErrorResponse) => {
  if (error.status === 401 && error.error?.errorCode === Constants.serverErrorCode.credentialsExpired) {
    const resetToken = error.error.resetToken;
    if (resetToken) {
      this.router.navigateByUrl(`login/resetExpiredPassword?resetToken=${resetToken}`);
      return of({
        token: '',
        refreshToken: '',
        scope: Authority.TENANT_ADMIN
      } as LoginResponse);
    }
  }
  return throwError(() => error);
})
```

**Recommendation:** Use Option A (remove logic) because the login component already has correct logic. Having it in both places creates redundancy and maintenance issues.

### Change 3: Ensure Login Component Error Handling Remains
**File:** `/ui-ngx/src/app/modules/login/pages/login/login.component.ts`

**Keep existing code (Lines 68-76) - NO CHANGES NEEDED:**
```typescript
(error: HttpErrorResponse) => {
  if (error && error.error && error.error.errorCode) {
    if (error.error.errorCode === Constants.serverErrorCode.credentialsExpired) {
      this.router.navigateByUrl(`login/resetExpiredPassword?resetToken=${error.error.resetToken}`);
    } else if (error.error.errorCode === Constants.serverErrorCode.passwordViolation) {
      this.passwordViolation = true;
    }
  }
}
```

This code is CORRECT and will handle the redirect once Changes 1 and 2 are implemented.

## Summary

The password expiry redirect is not working due to two critical issues:

1. **HTTP Interceptor Logic Error**: Using OR instead of AND causes credentialsExpired errors to be marked as "unhandled" and displayed as generic errors
2. **Auth Service Redirect Error**: Attempting to use resetToken (a UUID) as a URL for window.location.href

The fix requires:
- Changing OR to AND in the interceptor condition
- Removing or fixing the incorrect redirect logic in auth.service.ts
- The login component already has correct logic and needs no changes

Once fixed, expired password errors will properly propagate to the login component, which will redirect users to the password reset page with the correct resetToken parameter.
