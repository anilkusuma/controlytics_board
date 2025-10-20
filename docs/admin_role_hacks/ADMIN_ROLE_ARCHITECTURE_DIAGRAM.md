# ADMIN Role Implementation - Architecture Diagram

## High-Level Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ADMIN User Navigates to Users List                                     │
│ Route: /customers/{customerId}/users                                   │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Router Guard Checks Authority                                          │
│ Auth Check: [Authority.TENANT_ADMIN]                                   │
│ ADMIN has Authority.TENANT_ADMIN ✓ PASSES                              │
│ File: customer-routing.module.ts (lines 69, 107, 161, 177, 199)       │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ UsersTableConfigResolver.resolve() Executes                            │
│ File: users-table-config.resolver.ts (lines 128-179)                  │
│                                                                        │
│ Step 1: Extract authUser info                                         │
│   authUser.additionalInfo.role = UserRole.ADMIN                       │
│   authUser.authority = Authority.TENANT_ADMIN                         │
│                                                                        │
│ Step 2: Check if role === UserRole.ADMIN (line 134)                   │
│   YES - This is ADMIN user, special handling                          │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                ┌──────────────┴──────────────┐
                │                             │
                ▼                             ▼
        ┌──────────────────────┐    ┌──────────────────────┐
        │ Extract Parameters   │    │ Non-ADMIN User       │
        │ Line 135-136:        │    │ Standard Flow        │
        │ tenantId = user's    │    │ entitiesFetchFunction│
        │ customerId = route   │    │ = getTenantAdmins()  │
        │ param (NO VALIDATION)│    │ (single API call)    │
        └──────────────┬───────┘    └─────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────────────────────┐
        │ HACK #1: Multi-API Fusion                   │
        │ Lines 137-149: Create entitiesFetchFunction │
        │                                              │
        │ Calls forkJoin([                             │
        │   getTenantAdmins(tenantId),                 │
        │   getCustomerUsers(customerId)    ◄─ No     │
        │ ]) ◄─ Merged in Frontend!          validation│
        │                                              │
        │ Result: Combined users from 2 APIs          │
        └──────────────────────┬───────────────────────┘
                               │
                               ▼
        ┌──────────────────────────────────────────────┐
        │ Backend APIs Called (No Special Handling)    │
        │                                              │
        │ /api/tenant/{tenantId}/users                 │
        │   → Returns: All TENANT_ADMIN users          │
        │   → Filtered to: ADMIN or MAINTENANCE roles  │
        │                                              │
        │ /api/customer/{customerId}/users             │
        │   → Returns: All CUSTOMER_USER users         │
        │   → Authority check passes (TENANT_ADMIN)    │
        └──────────────────────┬───────────────────────┘
                               │
                               ▼
        ┌──────────────────────────────────────────────┐
        │ Frontend Merges Results                      │
        │ Lines 141-148: Create PageData object        │
        │                                              │
        │ Combined Data:                               │
        │  - ADMIN/MAINTENANCE users (from API 1)      │
        │  - ALL customer users (from API 2)           │
        │                                              │
        │ Pagination: MAX of both APIs                 │
        └──────────────────────┬───────────────────────┘
                               │
                               ▼
        ┌──────────────────────────────────────────────┐
        │ Users Table Displays with Actions            │
        │ Lines 102-114: deleteEnabled Function        │
        │                                              │
        │ Delete Action Check:                         │
        │ - Check UserRole (HACK #6)                   │
        │ - ADMIN → Delete HIDDEN                      │
        │ - CONTROLYTICS_ADMIN → Delete SHOWN          │
        │                                              │
        │ Edit/View Actions:                           │
        │ - Always available for ADMIN users           │
        └──────────────────────┬───────────────────────┘
                               │
                ┌──────────────┴──────────────┐
                │                             │
                ▼                             ▼
        ┌─────────────────────┐     ┌─────────────────┐
        │ ADMIN Clicks Edit   │     │ ADMIN Clicks    │
        │ User                │     │ Delete (hidden) │
        └────────┬────────────┘     └────────┬────────┘
                 │                           │
                 ▼                           ▼
        ┌──────────────────────┐   ┌───────────────────┐
        │ HACK #3: Re-auth     │   │ Cannot click -    │
        │ Dialog Appears       │   │ button hidden     │
        │ Lines 197-233        │   │                   │
        │ Only for ADMIN!      │   │ Via direct API:   │
        │                      │   │ DELETE allowed    │
        │ Re-auth Validation:  │   │ (backend check)   │
        │ None - Pure UX      │   │                   │
        └──────────┬───────────┘   └──────────────────┘
                   │
                   ▼
        ┌──────────────────────────────────┐
        │ HACK #4: Authority Override      │
        │ Lines 235-246: performSaveUser   │
        │                                  │
        │ If saving ADMIN/MAINTENANCE:     │
        │   user.authority = TENANT_ADMIN  │
        │   user.customerId = undefined    │
        │                                  │
        │ Else:                            │
        │   user.authority = CUSTOMER_USER │
        │                                  │
        │ This is Business Logic Rewrite   │
        └──────────────┬────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────────┐
        │ Backend: UserController.saveUser  │
        │ Lines 204-225                    │
        │                                  │
        │ @PreAuthorize("hasAnyAuthority(  │
        │  'TENANT_ADMIN', 'CUSTOMER_USER')│
        │                                  │
        │ Checks:                          │
        │ 1. Authority ✓ (TENANT_ADMIN)   │
        │ 2. checkEntity() → permissions  │
        │                                  │
        │ NO UserRole Check!               │
        └──────────────┬────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────────┐
        │ Backend: AccessControlService    │
        │ DefaultAccessControlService      │
        │                                  │
        │ Check: user.getAuthority()      │
        │ Result: TENANT_ADMIN allowed    │
        │                                  │
        │ NO UserRole Validation!          │
        └──────────────┬────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────────┐
        │ User Saved Successfully          │
        │                                  │
        │ What Happened:                   │
        │ - ADMIN can create/edit users    │
        │ - Cannot delete via UI           │
        │ - Could delete via direct API    │
        │ - Authority modified at save     │
        │ - No audit trail                 │
        └──────────────────────────────────┘
```

---

## Permission Checking Flow

```
┌──────────────────────────────────────────────────────────────┐
│ API Call: GET /api/user/{userId}                            │
│ Called by: ADMIN user                                        │
└────────────────────┬─────────────────────────────────────────┘
                     │
                     ▼
    ┌───────────────────────────────────────────┐
    │ UserController.getUserById()              │
    │ Lines 142-167                             │
    │                                           │
    │ @PreAuthorize("hasAnyAuthority(          │
    │   'SYS_ADMIN', 'TENANT_ADMIN',           │
    │   'CUSTOMER_USER')")                      │
    │                                           │
    │ ADMIN.authority = TENANT_ADMIN ✓ PASS    │
    └────────────────┬──────────────────────────┘
                     │
                     ▼
    ┌───────────────────────────────────────────┐
    │ checkUserId(userId, Operation.READ)       │
    │ BaseController.java lines 527-529         │
    └────────────────┬──────────────────────────┘
                     │
                     ▼
    ┌───────────────────────────────────────────────────┐
    │ checkEntityId(userId, userService::findUserById,  │
    │              Operation.READ)                      │
    │ BaseController.java lines 611-627                 │
    │                                                   │
    │ 1. validateId(userId)                            │
    │ 2. SecurityUser user = getCurrentUser()          │
    │ 3. E entity = findUserById(user.tenantId, userId)│
    │    ↑ Uses ADMIN's tenantId                       │
    │ 4. checkEntity(user, entity, Operation.READ)     │
    └────────────────┬──────────────────────────────────┘
                     │
                     ▼
    ┌───────────────────────────────────────────────────┐
    │ checkEntity(user, entity, Operation.READ)         │
    │ BaseController.java lines 623-627                 │
    │                                                   │
    │ accessControlService.checkPermission(             │
    │   user,                                           │
    │   Resource.USER,                                  │
    │   Operation.READ,                                 │
    │   entity.getId(),                                 │
    │   entity)                                         │
    └────────────────┬──────────────────────────────────┘
                     │
                     ▼
    ┌──────────────────────────────────────────┐
    │ DefaultAccessControlService              │
    │ checkPermission() - lines 58-66          │
    │                                          │
    │ getPermissionChecker(                    │
    │   user.getAuthority(),  ← TENANT_ADMIN   │
    │   Resource.USER)                         │
    │                                          │
    │ Get TenantAdminPermissions               │
    │ Check: hasPermission(user, READ, ...)   │
    │ Result: ✓ ALLOWED                       │
    │                                          │
    │ NO UserRole Check!                      │
    └────────────────┬──────────────────────────┘
                     │
                     ▼
    ┌──────────────────────────────────────────┐
    │ User Data Returned                       │
    │                                          │
    │ ADMIN CAN:                               │
    │ - Read any user in same tenant           │
    │ - Read users from managed customers      │
    │ - (if backend validates customer link)   │
    └──────────────────────────────────────────┘
```

---

## Delete Permission Check Flow

```
┌──────────────────────────────────────────────────────────────┐
│ Frontend: ADMIN User Views Delete Button                    │
│ deleteEnabled = user => { ... }                             │
│ Lines 102-114                                               │
└────────────────┬─────────────────────────────────────────────┘
                 │
     ┌───────────┴───────────┐
     │                       │
     ▼                       ▼
ADMIN Role              CONTROLYTICS_ADMIN Role
│                       │
├─ self-delete? NO      ├─ self-delete? NO
│                       │
├─ currentUserRole      ├─ currentUserRole
│  = UserRole.ADMIN     │  = UserRole.CONTROLYTICS_ADMIN
│                       │
├─ currentAuthority     ├─ currentAuthority
│  = TENANT_ADMIN       │  = TENANT_ADMIN or SYS_ADMIN
│                       │
├─ Check:               ├─ Check:
│  ADMIN ==             │  CONTROLYTICS_ADMIN ==
│  CONTROLYTICS_ADMIN?  │  CONTROLYTICS_ADMIN?
│  NO ✗                 │  YES ✓
│                       │
├─ Check Authority:     ├─ Check Authority:
│  TENANT_ADMIN ==      │  (already SYS_ADMIN) ✓
│  SYS_ADMIN?           │
│  NO ✗                 │
│                       │
▼                       ▼
Delete HIDDEN        Delete SHOWN
(deleteEnabled=false) (deleteEnabled=true)
Button is hidden      Button is visible


BUT! If ADMIN user bypasses and calls API directly:
─────────────────────────────────────────────────

DELETE /api/user/{userId}

@PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
public void deleteUser(@PathVariable String userId)

ADMIN.authority = TENANT_ADMIN ✓ PASSES

Then checkUserId(userId, Operation.DELETE)
→ Permission check with TENANT_ADMIN
→ TenantAdminPermissions allows DELETE
→ DELETE SUCCEEDS!

SECURITY ISSUE: Backend would allow DELETE
even though Frontend hides it!
```

---

## Data Flow: User Creation by ADMIN

```
ADMIN clicks "Add User"
    │
    ▼
Dialog opens: AddUserDialogComponent
File: add-user-dialog.component.ts
    │
    ├─ HACK #7: Check Role
    │  Line 91: if (role === UserRole.ADMIN)
    │         Show re-login dialog (forced)
    │  else (CONTROLYTICS_ADMIN)
    │         Skip re-login
    │
    ▼
User Enters Data & Clicks Submit
    │
    ├─ Form validation passes
    │
    ▼
performUserCreation() - Line 114
    │
    ├─ Set user.tenantId = tenantId (line 116)
    │
    ├─ Check role (lines 117-124)
    │  if ADMIN or MAINTENANCE:
    │    user.authority = TENANT_ADMIN (HACK #4)
    │    user.customerId = undefined
    │  else (OPERATOR, etc):
    │    user.authority = CUSTOMER_USER
    │    user.customerId = customerId
    │
    ▼
userService.saveUser(user, sendActivationEmail)
    │
    ▼
Backend: UserController.saveUser()
    │
    ├─ @PreAuthorize check PASSES (TENANT_ADMIN)
    │
    ├─ checkEntity() permission check PASSES
    │
    ├─ No UserRole validation
    │
    ▼
TbUserService.save()
    │
    ├─ userService.saveUser(tenantId, user)
    │
    ├─ Send activation email (if new user)
    │
    ├─ Log audit action
    │
    ▼
User Created Successfully
    │
    ├─ Authority written as TENANT_ADMIN
    │ (if role was ADMIN)
    │
    ├─ No audit of authority rewrite
    │
    ▼
Dialog Closes, User Added
```

---

## Security Vulnerabilities Mind Map

```
                        ┌─── ADMIN Role Security Issues ───┐
                        │                                   │
        ┌───────────────┼───────────────┬──────────────────┼──────────────┐
        │               │               │                  │              │
        ▼               ▼               ▼                  ▼              ▼
   Frontend          Authority      Permission        Re-auth         Audit
   Boundary          Confusion       Checking          Fake           Trail
   Issue             Problem         Gap                                
        │               │               │                  │              │
        ├─ customerId   ├─ ADMIN has    ├─ Backend       ├─ Only UI      ├─ No UserRole
        │  not validated│  TENANT_ADMIN │  checks        │  shows dialog  │  changes
        │               │               │                  │              │  tracked
        ├─ Route params ├─ CONTROLYTICS ├─ No UserRole   ├─ No password  ├─ No privilege
        │  from user    │  _ADMIN also  │  backend       │  validation    │  escalation
        │               │  TENANT_ADMIN │  check         │               │  logged
        ├─ No backend   ├─ Both same    ├─ Delete allows ├─ Session could├─ No authority
        │  ownership    │  authority    │  ADMIN backend │  be hijacked   │  rewrite
        │  check        │  level        │                │               │  logged
        │               │               │                │               │
        └───────────────┴───────────────┴────────────────┴───────────────┴──────────────
              Risk: MODERATE          Risk: MODERATE      Risk: LOW      Risk: MODERATE
```

---

## Files Modified & Code Paths

```
FRONTEND FILES:

1. users-table-config.resolver.ts
   ├─ Lines 134-149: ADMIN user list fetch (HACK #1, #2)
   ├─ Lines 197-233: Save with re-auth (HACK #3)
   ├─ Lines 235-246: Authority override (HACK #4)
   ├─ Lines 267-273: Direct navigation (HACK #5)
   ├─ Lines 102-114: Delete hiding (HACK #6)
   ├─ Lines 341-361: Temp password re-auth (HACK #7)
   └─ Lines 396-416: Credentials re-auth

2. add-user-dialog.component.ts
   ├─ Lines 91-112: Add user re-auth for ADMIN (HACK #7)
   └─ Lines 117-124: Authority override (HACK #4)

3. user.model.ts
   ├─ UserRole enum definition
   └─ Authority enum via inheritance

4. customer-routing.module.ts
   └─ Auth guards: [Authority.TENANT_ADMIN]

BACKEND FILES:

1. UserController.java
   ├─ getUserById() lines 142-167
   ├─ getTenantAdmins() lines 465-482
   ├─ getCustomerUsers() lines 489-508
   ├─ deleteUser() lines 378-394
   └─ setUserCredentialsEnabled() lines 510-551

2. BaseController.java
   ├─ checkUserId() lines 527-529
   ├─ checkCustomerId() lines 523-525
   └─ checkEntityId() lines 611-627

3. DefaultAccessControlService.java
   └─ checkPermission() - AUTHORITY ONLY, no UserRole

4. AccessControlService.java
   └─ Interface definition
```

