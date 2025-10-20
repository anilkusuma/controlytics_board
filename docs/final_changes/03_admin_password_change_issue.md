# Requirement

## Main Issue
After admin changes their password and logs in with the new password, a password confirmation popup (relogin dialog) appears unexpectedly:
- Sometimes appears immediately after login
- Sometimes appears after idle time
- Even after refreshing the webpage and re-logging in, the issue persists
- Closing the popup without entering password allows normal usage

## Sub-requirement
After admin successfully changes their password, show a "Password changed successfully" popup message.

---

# Current Implementation as per Code

## Password Change Flow

### Frontend (UI Layer)

**File: `/ui-ngx/src/app/modules/home/pages/security/security.component.ts`**

1. **Password Change Handler** (Lines 352-376):
   ```typescript
   onChangePassword(form: FormGroupDirective): void {
     if (this.changePassword.valid) {
       this.authService.changePassword(
         this.changePassword.get('currentPassword').value,
         this.changePassword.get('newPassword').value,
         {ignoreErrors: true}
       ).subscribe(() => {
         this.discardChanges(form);
       }, (error) => {
         // Error handling
       });
     }
   }
   ```

**File: `/ui-ngx/src/app/core/auth/auth.service.ts`**

2. **Change Password Service** (Lines 197-203):
   ```typescript
   public changePassword(currentPassword: string, newPassword: string, config?: RequestConfig) {
     return this.http.post('/api/auth/changePassword',
       {currentPassword, newPassword},
       defaultHttpOptionsFromConfig(config)
     ).pipe(
       tap((loginResponse: LoginResponse) => {
         this.setUserFromJwtToken(loginResponse.token, loginResponse.refreshToken, false);
       })
     );
   }
   ```
   - Receives new JWT token pair from server
   - Updates tokens in session storage via `setUserFromJwtToken(token, refreshToken, false)`

**File: `/ui-ngx/src/app/modules/home/pages/profile/profile.component.ts`**

3. **Profile Save with Token Refresh** (Lines 75-102):
   ```typescript
   save(): void {
     this.userService.saveUser(this.user).subscribe(
       (user) => {
         this.userLoaded(user);
         this.store.dispatch(new ActionAuthUpdateUserDetails({...}));
         this.store.dispatch(new ActionSettingsChangeLanguage({...}));
         this.authService.refreshJwtToken(false);  // <-- Called after profile update
       }
     );
   }
   ```

### Backend (Server Layer)

**File: `/application/src/main/java/org/thingsboard/server/controller/AuthController.java`**

4. **Change Password Endpoint** (Lines 107-125):
   ```java
   @RequestMapping(value = "/auth/changePassword", method = RequestMethod.POST)
   public JwtPair changePassword(@RequestBody ChangePasswordRequest changePasswordRequest) {
     String currentPassword = changePasswordRequest.getCurrentPassword();
     String newPassword = changePasswordRequest.getNewPassword();
     // Validate current password
     // Validate new password policy
     // Encode and save new password
     userCredentials.setPassword(passwordEncoder.encode(newPassword));
     userService.replaceUserCredentials(securityUser.getTenantId(), userCredentials);

     // Invalidate all existing tokens
     eventPublisher.publishEvent(new UserCredentialsInvalidationEvent(securityUser.getId()));

     // Return new token pair
     return tokenFactory.createTokenPair(securityUser);
   }
   ```

**File: `/application/src/main/java/org/thingsboard/server/service/security/auth/DefaultTokenOutdatingService.java`**

5. **Token Outdating Service** (Lines 43-68):
   ```java
   @EventListener(classes = UserAuthDataChangedEvent.class)
   public void onUserAuthDataChanged(UserAuthDataChangedEvent event) {
     if (StringUtils.hasText(event.getId())) {
       cache.put(event.getId(), event.getTs());  // Mark timestamp for token invalidation
     }
   }

   @Override
   public boolean isOutdated(String token, UserId userId) {
     Claims claims = tokenFactory.parseTokenClaims(token).getBody();
     long issueTime = claims.getIssuedAt().getTime();
     String sessionId = claims.get("sessionId", String.class);
     if (isTokenOutdated(issueTime, userId.toString())){
       return true;
     } else {
       return sessionId != null && isTokenOutdated(issueTime, sessionId);
     }
   }
   ```

### Token Refresh and Validation Flow

**File: `/ui-ngx/src/app/core/interceptors/global-http-interceptor.ts`**

6. **HTTP Interceptor** (Lines 100-143):
   - When receiving 401 error or JWT token expired error:
     ```typescript
     if (errorCode === Constants.serverErrorCode.jwtTokenExpired) {
       return this.refreshTokenAndRetry(req, next);
     }
     ```
   - `refreshTokenAndRetry()` (Lines 153-162):
     ```typescript
     return this.authService.refreshJwtToken().pipe(
       catchError((err: Error) => {
         this.authService.logout(true, true);
         // Shows error and redirects to login
       }),
       switchMap(() => this.jwtIntercept(req, next))
     );
     ```

### Relogin Dialog (Password Confirmation Popup)

**File: `/ui-ngx/src/app/modules/home/dialogs/re-login/relogin-dialog.component.ts`**

7. **Relogin Dialog Component**:
   - Used for security-sensitive operations requiring password re-verification
   - Invoked via `dialogService.relogin()` in various places:
     - User management operations (add/delete/modify users)
     - Security settings changes
     - Audit log operations
   - **NOT directly related to password change flow**

---

# Expectation after the change

1. **Password Change Success Flow**:
   - Admin changes password in Security settings
   - System validates and updates password successfully
   - Show "Password changed successfully" popup notification
   - New JWT token pair is issued and stored
   - Admin continues working without interruption
   - No unexpected password confirmation dialogs appear

2. **Token Management**:
   - Old tokens are properly invalidated
   - New tokens are seamlessly used for all subsequent requests
   - No race conditions between token invalidation and token refresh
   - WebSocket connections properly handle token updates

3. **Session Continuity**:
   - Admin can continue using the application immediately
   - No forced logout or re-login required
   - No popup dialogs asking for password confirmation
   - Page refreshes work correctly with new credentials

---

# Changes required

## 1. Add Success Notification after Password Change

**File: `/ui-ngx/src/app/modules/home/pages/security/security.component.ts`**

**Change the `onChangePassword` method** (Lines 352-376):

```typescript
onChangePassword(form: FormGroupDirective): void {
  if (this.changePassword.valid) {
    this.authService.changePassword(this.changePassword.get('currentPassword').value,
      this.changePassword.get('newPassword').value, {ignoreErrors: true}).subscribe(() => {
        this.discardChanges(form);

        // ADD THIS: Show success notification
        this.store.dispatch(new ActionNotificationShow({
          message: this.translate.instant('security.password-changed-successfully'),
          type: 'success',
          duration: 3000,
          verticalPosition: 'bottom',
          horizontalPosition: 'right'
        }));
      },
      (error) => {
        if (error.status === 400 && error.error.message === 'Current password doesn\'t match!') {
          this.changePassword.get('currentPassword').setErrors({differencePassword: true});
        } else if (error.status === 400 && error.error.message.startsWith('Password must')) {
          this.loadPasswordPolicy();
        } else if (error.status === 400 && error.error.message.startsWith('Password was already used')) {
          this.changePassword.get('newPassword').setErrors({alreadyUsed: error.error.message});
        } else {
          this.store.dispatch(new ActionNotificationShow({
            message: error.error.message,
            type: 'error',
            target: 'changePassword'
          }));
        }
      });
  } else {
    this.changePassword.markAllAsTouched();
  }
}
```

## 2. Fix Token Race Condition Issue

**File: `/ui-ngx/src/app/core/auth/auth.service.ts`**

**Modify the `refreshJwtToken` method** to handle password change scenario (Lines 495-533):

The issue is in line 502: `this.setUserFromJwtToken(null, null, false);` which clears the current JWT token before the refresh token call completes. This creates a window where:
1. Password change returns new tokens and stores them
2. Some background request (WebSocket, pending HTTP request) triggers token refresh
3. Token refresh clears the newly set tokens (line 502)
4. This causes token mismatch and triggers relogin dialog

**Solution**: Add a check to prevent clearing tokens if they were recently updated:

```typescript
public refreshJwtToken(loadUserElseStoreJwtToken = true): Observable<LoginResponse> {
  let response: Observable<LoginResponse> = this.refreshTokenSubject;
  if (this.refreshTokenSubject === null) {
    this.refreshTokenSubject = new ReplaySubject<LoginResponse>(1);
    response = this.refreshTokenSubject;
    const refreshToken = AuthService._storeGet('refresh_token');
    const refreshTokenValid = AuthService.isTokenValid('refresh_token');

    // MODIFY THIS: Don't clear tokens if they're still valid (recently updated)
    // This prevents race condition after password change
    const jwtTokenValid = AuthService.isJwtTokenValid();
    if (!jwtTokenValid) {
      this.setUserFromJwtToken(null, null, false);
    }

    if (!refreshTokenValid) {
      this.translate.get('access.refresh-token-expired').subscribe(
        (translation) => {
          this.refreshTokenSubject.error(new Error(translation));
          this.refreshTokenSubject = null;
        }
      );
    } else {
      const refreshTokenRequest = {
        refreshToken
      };
      const refreshObservable = this.http.post<LoginResponse>('/api/auth/token', refreshTokenRequest, defaultHttpOptions());
      refreshObservable.subscribe((loginResponse: LoginResponse) => {
        if (loadUserElseStoreJwtToken) {
          this.setUserFromJwtToken(loginResponse.token, loginResponse.refreshToken, false);
        } else {
          this.updateAndValidateTokens(loginResponse.token, loginResponse.refreshToken, true);
        }
        this.updatedAuthUserFromToken(loginResponse.token);
        this.refreshTokenSubject.next(loginResponse);
        this.refreshTokenSubject.complete();
        this.refreshTokenSubject = null;
      }, () => {
        this.clearJwtToken();
        this.refreshTokenSubject.error(new Error(this.translate.instant('access.refresh-token-failed')));
        this.refreshTokenSubject = null;
      });
    }
  }
  return response;
}
```

## 3. Add Translation Key for Success Message

**File: `/ui-ngx/src/assets/locale/locale.constant-en_US.json`** (and other locale files)

Add the translation key:
```json
{
  "security": {
    "password-changed-successfully": "Password changed successfully",
    ...
  }
}
```

## 4. Optional: Add Debounce to Token Refresh Calls

**File: `/ui-ngx/src/app/modules/home/pages/profile/profile.component.ts`**

Remove the unnecessary `refreshJwtToken(false)` call after profile save (Line 99), as the password change endpoint already returns fresh tokens:

```typescript
save(): void {
  this.user = {...this.user, ...this.profile.value};
  if (!this.user.additionalInfo) {
    this.user.additionalInfo = {};
  }
  this.user.additionalInfo.lang = this.profile.get('language').value;
  this.user.additionalInfo.homeDashboardId = this.profile.get('homeDashboardId').value;
  this.user.additionalInfo.homeDashboardHideToolbar = this.profile.get('homeDashboardHideToolbar').value;
  this.userService.saveUser(this.user).subscribe(
    (user) => {
      this.userLoaded(user);
      this.store.dispatch(new ActionAuthUpdateUserDetails({ userDetails: {
          additionalInfo: {...user.additionalInfo},
          authority: user.authority,
          createdTime: user.createdTime,
          tenantId: user.tenantId,
          customerId: user.customerId,
          email: user.email,
          phone: user.phone,
          firstName: user.firstName,
          id: user.id,
          lastName: user.lastName,
        } }));
      this.store.dispatch(new ActionSettingsChangeLanguage({ userLang: user.additionalInfo.lang }));
      // REMOVE THIS LINE: this.authService.refreshJwtToken(false);
      // Not needed as changePassword already provides fresh tokens
    }
  );
}
```

---

# Root Cause Analysis

## Primary Root Cause: Token Refresh Race Condition

### The Problem Flow:

1. **Admin changes password** → Backend publishes `UserCredentialsInvalidationEvent` which marks timestamp in cache
2. **Backend returns new JWT token pair** → Frontend stores new tokens via `setUserFromJwtToken()`
3. **Concurrent operations** (WebSocket reconnection, pending HTTP requests, background timers):
   - Detect old token is about to expire or marked outdated
   - Trigger `refreshJwtToken()` call
4. **Race condition in `refreshJwtToken()` at line 502**:
   ```typescript
   this.setUserFromJwtToken(null, null, false);  // Clears newly set tokens!
   ```
   - This line executes BEFORE checking if tokens are valid
   - Clears the newly set tokens from password change
   - Creates a window where tokens are null
5. **Subsequent HTTP requests fail** with 401 due to missing tokens
6. **HTTP Interceptor** catches 401 error → tries to refresh → fails → triggers logout or shows error
7. **System detects authentication failure** → triggers relogin dialog

### Why It's Intermittent:

The issue appears "sometimes" because it depends on:
- **Timing**: Whether WebSocket reconnection or background HTTP request happens during password change
- **Network latency**: Slower networks increase the race condition window
- **System load**: Busy systems may delay token storage operations
- **Browser tab activity**: Idle tabs may trigger different timing for background operations

### Secondary Contributing Factor: Profile Save Token Refresh

In `/ui-ngx/src/app/modules/home/pages/profile/profile.component.ts` (line 99):
```typescript
this.authService.refreshJwtToken(false);
```

This call is unnecessary because:
- The `changePassword()` method already returns and stores fresh tokens
- This extra call can trigger another race condition
- It's redundant and adds complexity

### Why Closing Popup Works:

When user closes the relogin dialog without entering password:
- The application continues with cached state
- Background token refresh eventually succeeds
- System recovers from the race condition
- This confirms the issue is transient token management, not permanent authentication failure

## Solution Summary:

The fix involves:
1. **Preventing premature token clearing**: Check token validity before clearing in `refreshJwtToken()`
2. **Adding success notification**: User feedback for password change completion
3. **Removing redundant refresh calls**: Eliminate unnecessary token refresh operations
4. **Ensuring proper token lifecycle**: New tokens from password change are respected by all operations

This addresses the root cause of tokens being cleared prematurely during the password change flow, eliminating the race condition that causes unexpected relogin dialogs.
