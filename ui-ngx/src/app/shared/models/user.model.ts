///
/// Copyright © 2016-2024 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { BaseData } from './base-data';
import { UserId } from './id/user-id';
import { CustomerId } from './id/customer-id';
import { Authority } from './authority.enum';
import { TenantId } from './id/tenant-id';
import { HasTenantId } from '@shared/models/entity.models';

export interface User extends BaseData<UserId>, HasTenantId {
  tenantId: TenantId;
  customerId: CustomerId;
  email: string;
  phone: string;
  authority: Authority;
  firstName: string;
  lastName: string;
  additionalInfo: any;
}

export enum UserRole {
  ADMIN = 'admin',
  OPERATOR = 'operator',
  MAINTENANCE = 'maintenance',
  SUPERVISOR = 'supervisor',
  CONTROLYTICS_ADMIN = 'controlytics_admin',
}

export const UserRoleUtils = {
  getDisplayName(role: UserRole): string {
    switch (role) {
      case UserRole.ADMIN:
        return 'Admin';
      case UserRole.OPERATOR:
        return 'Operator';
      case UserRole.MAINTENANCE:
        return 'Maintenance Personnel';
      case UserRole.SUPERVISOR:
        return 'Supervisor';
      case UserRole.CONTROLYTICS_ADMIN:
        return 'Controlytics Admin';
      default:
        return 'Unknown Role';
    }
  }
};

export enum ActivationMethod {
  DISPLAY_ACTIVATION_LINK = 'DISPLAY_ACTIVATION_LINK',
  SEND_ACTIVATION_MAIL = 'SEND_ACTIVATION_MAIL',
  DISPLAY_TEMPORARY_PASSWORD = 'DISPLAY_TEMPORARY_PASSWORD'
}

export const activationMethodTranslations = new Map<ActivationMethod, string>(
  [
    [ActivationMethod.DISPLAY_ACTIVATION_LINK, 'user.display-activation-link'],
    [ActivationMethod.SEND_ACTIVATION_MAIL, 'user.send-activation-mail'],
    [ActivationMethod.DISPLAY_TEMPORARY_PASSWORD, 'user.display-activation-password']
  ]
);

export interface AuthUser {
  sub: string;
  scopes: string[];
  userId: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  tenantId: string;
  customerId: string;
  isPublic: boolean;
  authority: Authority;
}

export interface UserEmailInfo {
  id: UserId;
  email: string;
  firstName: string;
  lastName: string;
}
