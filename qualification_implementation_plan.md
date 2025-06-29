# Qualification Implementation Plan for Controlytics Board

## Overview
This document provides a detailed implementation plan for the qualification requirements. Each requirement is analyzed with specific file changes, implementation details, and any open questions that need clarification.

## 1. Temporary Password Cannot Be Set as Current Password

### Requirement
When a temporary password is given and user is using the same temporary password as new password, the system should not allow it.

### Current Implementation Analysis
- Password reset flow is handled in `/application/src/main/java/org/thingsboard/server/controller/AuthController.java`
- Password validation is done in `/application/src/main/java/org/thingsboard/server/service/security/auth/DefaultAuthService.java`
- Frontend password reset forms are in:
  - `/ui-ngx/src/app/modules/login/pages/login/reset-password.component.ts`
  - `/ui-ngx/src/app/modules/login/pages/login/create-password.component.ts`

### Files to Modify

#### Backend Changes
1. **`/application/src/main/java/org/thingsboard/server/service/security/auth/DefaultAuthService.java`**
   - Add validation in `activateUser()` and `resetPassword()` methods
   - Compare new password with the temporary password/reset token
   - Throw appropriate exception if they match

2. **`/common/data/src/main/java/org/thingsboard/server/common/data/exception/ThingsboardErrorCode.java`**
   - Add new error code: `TEMPORARY_PASSWORD_REUSE_NOT_ALLOWED`

3. **`/application/src/main/resources/messages.properties`**
   - Add error message: "Cannot use temporary password as your new password"

#### Frontend Changes
1. **`/ui-ngx/src/app/modules/login/pages/login/reset-password.component.ts`**
   - Add client-side validation to check if new password matches temporary password
   - Display appropriate error message

2. **`/ui-ngx/src/app/modules/login/pages/login/create-password.component.ts`**
   - Similar validation for the create password flow

### Open Questions
1. Should this validation also apply to activation passwords when creating new users? - Yes, it should apply to all scenarios where a temporary password is used.
2. Should we store a hash of the temporary password for comparison, or is the plain text comparison acceptable 
   during the reset flow? - Hashing is recommended.

---

## 2. Disable Copy & Paste in Password Fields

### Requirement
Copy & paste options shouldn't work where passwords need to be entered: Login page, resetting password, Reports download authentication, setpoints Change.

### Current Implementation Analysis
Found password input fields in the following locations:
- Login: `/ui-ngx/src/app/modules/login/pages/login/login.component.html:54`
- Reset Password: `/ui-ngx/src/app/modules/login/pages/login/reset-password.component.html`
- Create Password: `/ui-ngx/src/app/modules/login/pages/login/create-password.component.html`
- Re-login Dialog: `/ui-ngx/src/app/modules/home/dialogs/re-login/relogin-dialog.component.html:88`
- Security Settings: `/ui-ngx/src/app/modules/home/pages/security/security.component.html`

### Files to Modify

1. **Create a Global Directive: `/ui-ngx/src/app/shared/directives/no-copy-paste.directive.ts`**
   ```typescript
   @Directive({
     selector: '[tbNoCopyPaste]'
   })
   export class NoCopyPasteDirective {
     @HostListener('paste', ['$event']) onPaste(e: Event) {
       e.preventDefault();
       return false;
     }
     
     @HostListener('copy', ['$event']) onCopy(e: Event) {
       e.preventDefault();
       return false;
     }
     
     @HostListener('cut', ['$event']) onCut(e: Event) {
       e.preventDefault();
       return false;
     }
   }
   ```

2. **Register directive in `/ui-ngx/src/app/shared/shared.module.ts`**

3. **Apply directive to all password fields:**
   - `/ui-ngx/src/app/modules/login/pages/login/login.component.html`
   - `/ui-ngx/src/app/modules/login/pages/login/reset-password.component.html`
   - `/ui-ngx/src/app/modules/login/pages/login/create-password.component.html`
   - `/ui-ngx/src/app/modules/home/dialogs/re-login/relogin-dialog.component.html`
   - `/ui-ngx/src/app/modules/home/pages/security/security.component.html`

### Implementation Details
Add `tbNoCopyPaste` directive to all password input fields:
```html
<input matInput type="password" formControlName="password" tbNoCopyPaste />
```

### Open Questions
1. Should we also disable right-click context menu on these fields?
2. Should we show a toast notification when users try to copy/paste?
3. Should this also apply to username/email fields for consistency?

---

## 3. Hide "Powered by TRH-MONITORING v3.6.2" Line

### Requirement
The "Powered by TRH-MONITORING v3.6.2" line should be hidden.

### Current Implementation Analysis
Found in two locations:
1. Login page footer: `/ui-ngx/src/app/modules/login/pages/login/login.component.html:79`
2. Within login form: `/ui-ngx/src/app/modules/login/pages/login/login.component.html:70`

### Files to Modify

1. **`/ui-ngx/src/app/modules/login/pages/login/login.component.html`**
   - Remove or comment out lines 67-71 (favicon logo container)
   - Remove or comment out lines 77-81 (powered by footer)

### Implementation Details
Remove the following sections:
```html
<!-- Remove this section -->
<div fxLayout="row" fxLayoutAlign="center center" class="favicon-logo-container" style="margin-top: 5px;">
  <img src="controlytics_ai.ico" alt="favicon-logo" class="favicon-logo"
       style="height: 24px; margin-right: 8px;">
  <span class="favicon-text mat-typography">TRH-MONITORING v.3.6.2</span>
</div>

<!-- And this section -->
<div ngClass="tb-login-version tb-powered-by-footer">
  <section data-html2canvas-ignore [ngStyle]="{'color': '#305680'}">
    <span>Powered by <a href="https://controlytics.ai" target="_blank">TRH-MONITORING v.3.6.2</a></span>
  </section>
</div>
```