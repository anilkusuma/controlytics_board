# ADMIN Role Implementation - Quick Reference

## File Structure

### Key Frontend Files

1. **User Model Definition**
   - Path: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/shared/models/user.model.ts`
   - Defines: UserRole enum (ADMIN, OPERATOR, MAINTENANCE, SUPERVISOR, CONTROLYTICS_ADMIN)
   - Defines: Authority enum via inheritance

2. **Users Table Configuration (PRIMARY FILE)**
   - Path: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/user/users-table-config.resolver.ts`
   - Contains: ADMIN-specific logic for user list fetching
   - Key Lines:
     - 134-149: ADMIN role user list fetching with forkJoin hack
     - 197-233: Re-authentication for user save
     - 235-246: Authority override in performSaveUser
     - 261-265: Delete user handling
     - 341-361: Re-auth for temporary password display
     - 396-416: Re-auth for user credentials enable/disable

3. **User Dialog Component**
   - Path: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/user/add-user-dialog.component.ts`
   - Contains: ADMIN re-authentication for user creation (Lines 91-112)
   - Contains: Authority override logic (Lines 117-124)

4. **Customer Routing Module**
   - Path: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/ui-ngx/src/app/modules/home/pages/customer/customer-routing.module.ts`
   - Defines: Route structure for customer users
   - Auth restriction: Authority.TENANT_ADMIN (Lines 69, 107, 161, 177, 199, etc.)

### Key Backend Files

1. **UserController**
   - Path: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/UserController.java`
   - getUserById() - Lines 142-167 (permission checks)
   - getCustomerUsers() - Lines 489-508 (API for fetching customer users)
   - getTenantAdmins() - Lines 465-482 (API for fetching tenant admins)
   - deleteUser() - Lines 378-394 (delete permission check)
   - setUserCredentialsEnabled() - Lines 510-551 (enable/disable user)

2. **BaseController**
   - Path: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/controller/BaseController.java`
   - checkUserId() - Lines 527-529 (user permission check)
   - checkCustomerId() - Lines 523-525 (customer permission check)
   - checkEntityId() - Lines 611-627 (generic permission validation)

3. **AccessControlService**
   - Path: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/service/security/permission/AccessControlService.java`
   - Interface defining permission checks

4. **DefaultAccessControlService**
   - Path: `/Users/anilkusuma/Documents/PersonalCodeWorkspace/Controlytics/controlytics_board/application/src/main/java/org/thingsboard/server/service/security/permission/DefaultAccessControlService.java`
   - Implementation: Authority-based permission checking only

---

## Seven Key Hacks Identified

### Hack #1: Multi-API Fusion (Frontend Data Merging)
**Location**: `users-table-config.resolver.ts` lines 137-149
**What**: Calls two separate APIs and merges results in frontend
**APIs Called**:
- `getTenantAdmins(tenantId)` - Gets TENANT_ADMIN users
- `getCustomerUsers(customerId)` - Gets CUSTOMER_USER users
**Why It's a Hack**: Custom business logic for ADMIN role, not standard permission-based
**Risk**: ADMIN could potentially access any customer if customerId validation is weak

### Hack #2: CustomerId Parameter Passthrough  
**Location**: `users-table-config.resolver.ts` lines 134-136
**What**: Takes customerId directly from route parameter without validation
**Code**: `this.customerId = routeParams.customerId;`
**Why It's a Hack**: No backend verification that ADMIN is authorized for this customer
**Risk**: ADMIN with knowledge of other customer IDs could access them

### Hack #3: Re-authentication Gate for ADMIN
**Location**: `users-table-config.resolver.ts` lines 197-233
**What**: Forces ADMIN to re-authenticate before saving ANY user
**Why It's a Hack**: 
- Not a backend requirement
- Pure UX confirmation, not cryptographic security
- Re-auth dialog doesn't validate permissions
**Risk**: Attackers with session hijacking could bypass

### Hack #4: Authority Override at Save Time
**Location**: `users-table-config.resolver.ts` lines 235-246
**What**: Modifies user.authority before sending to backend
**Code**: 
```typescript
if (user.additionalInfo.role === UserRole.ADMIN || user.additionalInfo.role === UserRole.MAINTENANCE) {
  user.authority = Authority.TENANT_ADMIN;
  user.customerId = undefined;
}
```
**Why It's a Hack**: Frontend business logic rewriting user permissions
**Risk**: Compromised frontend JS could escalate user privileges

### Hack #5: Direct User Details Navigation
**Location**: `users-table-config.resolver.ts` lines 267-273
**What**: Navigates to user details without pre-flight permission check
**Code**: `this.router.navigateByUrl(url);` - directly to user detail page
**Why It's a Hack**: Relies entirely on backend to validate access
**Risk**: Race condition or timing attack could access unauthorized user

### Hack #6: Role-Based Delete Hiding
**Location**: `users-table-config.resolver.ts` lines 102-114
**What**: Delete button hidden for ADMIN but shown for CONTROLYTICS_ADMIN
**Why It's a Hack**: 
- Backend PreAuthorize would still allow ADMIN to delete (TENANT_ADMIN authority)
- Only frontend enforces the UserRole restriction
**Risk**: Direct API call to deleteUser would work for ADMIN

### Hack #7: Asymmetric Re-authentication
**Location**: Multiple locations (lines 341-361, 396-416, and add-user-dialog.component.ts 91-112)
**What**: ADMIN users re-authenticate for sensitive ops, CONTROLYTICS_ADMIN does not
**Operations**: Save, temporary password, enable/disable credentials, add user
**Why It's a Hack**: Different trust levels without formal justification
**Risk**: Attackers could impersonate CONTROLYTICS_ADMIN roles

---

## Critical Findings

### 1. Authority vs UserRole Confusion
- **Authority**: Platform-level (TENANT_ADMIN, CUSTOMER_USER, SYS_ADMIN)
- **UserRole**: Application-level (ADMIN, OPERATOR, MAINTENANCE, CONTROLYTICS_ADMIN)
- **Problem**: Backend only checks Authority, not UserRole
- **ADMIN Issue**: Has Authority.TENANT_ADMIN so can do anything a TENANT_ADMIN can do

### 2. No Backend ADMIN-to-Customer Mapping
- System doesn't validate which customers an ADMIN can access
- Relies on frontend routing to prevent cross-customer access
- If customer ID is guessed or leaked, ADMIN could access it

### 3. Backend Delete Allows ADMIN
- `deleteUser()` has `@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")`
- ADMIN has TENANT_ADMIN authority
- Backend would allow delete even if frontend hides button

### 4. Permission Checking is Authority-Only
- `DefaultAccessControlService` only checks `user.getAuthority()`
- No UserRole dimension in permission checking
- ADMIN and CONTROLYTICS_ADMIN would have identical backend access if Authority is same

### 5. Re-authentication is Frontend-Only
- Backend doesn't know or care about re-authentication
- Dialog provides UX confirmation, not cryptographic security
- Token interceptors could bypass this

---

## What ADMIN Users Can Actually Do

### Via UI (Frontend)
1. View users in managed customers - ALLOWED
2. View user details - ALLOWED
3. Create users (after re-auth) - ALLOWED
4. Edit users (after re-auth) - ALLOWED
5. Delete users - BLOCKED (UI hides delete button)
6. View temporary password - BLOCKED (UI hidden, requires re-auth)
7. Enable/disable user credentials - BLOCKED (UI requires re-auth, probably hidden)

### Via Direct API Calls (Backend)
1. GET /api/user/{userId} - ALLOWED (has TENANT_ADMIN authority)
2. POST /api/user (save user) - ALLOWED (has TENANT_ADMIN authority)
3. DELETE /api/user/{userId} - ALLOWED (has TENANT_ADMIN authority, no UserRole check)
4. POST /api/user/{userId}/userCredentialsEnabled - ALLOWED (has TENANT_ADMIN authority)
5. GET /api/user/{userId}/temporaryPassword - ALLOWED (has TENANT_ADMIN authority)
6. GET /customer/{customerId}/users - ALLOWED (has TENANT_ADMIN authority)
7. GET /tenant/{tenantId}/users - ALLOWED (has TENANT_ADMIN authority)

---

## Security Assessment

### GREEN FLAGS (Good Practices)
- Re-authentication for sensitive operations (even if frontend-only)
- Authority-based permission framework
- Backend permission validation on all APIs
- Separated Authority and UserRole concerns

### RED FLAGS (Security Concerns)
- No UserRole-based backend permission checking
- Frontend-driven permission boundaries (customerId from route params)
- Delete button hidden but backend would allow
- No audit logging for privilege escalation
- No database mapping of ADMIN to allowed customers

---

## Recommended Next Steps

1. **Verify Backend Customer Mapping**
   - Check if there's a table mapping ADMIN users to customers they can manage
   - Look in database schema for this relationship

2. **Test API Privilege Escalation**
   - Call DELETE as ADMIN user directly
   - Try accessing other customer users via API
   - Check if backend properly restricts cross-customer access

3. **Audit Trail Review**
   - Check if deletes by ADMIN are logged
   - Check if privilege modifications are audited
   - Look for any unauthorized access attempts

4. **Permission Architecture Review**
   - Consider implementing UserRole at backend
   - Add explicit ADMIN→Customer mappings
   - Implement delete restriction in backend, not just frontend

