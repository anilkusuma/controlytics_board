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

import {ChangeDetectorRef, Component, Inject, Optional, ViewChild} from '@angular/core';
import {select, Store} from '@ngrx/store';
import {AppState} from '@core/core.state';
import {EntityComponent} from '../../components/entity/entity.component';
import {UntypedFormBuilder, UntypedFormGroup, Validators} from '@angular/forms';
import {User, UserRole} from '@shared/models/user.model';
import {selectAuth} from '@core/auth/auth.selectors';
import {map} from 'rxjs/operators';
import {Authority} from '@shared/models/authority.enum';
import {isDefinedAndNotNull} from '@core/utils';
import {EntityTableConfig} from '@home/models/entity/entities-table-config.models';
import {ActionNotificationShow} from '@app/core/notification/notification.actions';
import {TranslateService} from '@ngx-translate/core';
import {EntityType} from "@shared/models/entity-type.models";
import {MatSelectChange} from "@angular/material/select";

@Component({
  selector: 'tb-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss']
})
export class UserComponent extends EntityComponent<User> {

  authority: Authority;
  userRole: string;
  selectedRole = '';

  @ViewChild('tbEntityList') tbEntityList;

  loginAsUserEnabled$ = this.store.pipe(
    select(selectAuth),
    map((auth) => auth.userTokenAccessEnabled)
  );

  constructor(protected store: Store<AppState>,
              @Optional() @Inject('entity') protected entityValue: User,
              @Optional() @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<User>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef,
              protected translate: TranslateService) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
    store.select(selectAuth).subscribe(auth => {
        this.authority = auth.authUser.authority;
        this.userRole = auth.userDetails.additionalInfo?.role;
    });
  }

  hideDelete() {
    if (this.entitiesTableConfig) {
      return !this.entitiesTableConfig.deleteEnabled(this.entity);
    } else {
      return false;
    }
  }

  isUserCredentialsEnabled(): boolean {
      return this.entity.additionalInfo.userCredentialsEnabled === true;
  }

  isUserCredentialPresent(): boolean {
    return isDefinedAndNotNull(this.entity?.additionalInfo?.userCredentialsEnabled);
  }

  isForgotPasswordEnabled(): boolean {
    return false;
  }

  isTemporaryPasswordEnabled(): boolean {
    return isDefinedAndNotNull(this.entity?.additionalInfo?.resetPasswordTokenEnabled) &&
      this.entity.additionalInfo.resetPasswordTokenEnabled === true;
  }

  buildForm(entity: User): UntypedFormGroup {
    this.selectedRole = entity && entity.additionalInfo && entity.additionalInfo.role ? entity.additionalInfo.role : null;
    return this.fb.group(
      {
        email: [entity ? entity.email : '', [Validators.required, Validators.minLength(3),
          Validators.pattern(/^[0-9]*$/)]],
        firstName: [entity ? entity.firstName : ''],
        lastName: [entity ? entity.lastName : ''],
        phone: [entity ? entity.phone : ''],
        additionalInfo: this.fb.group(
          {
            description: [entity && entity.additionalInfo ? entity.additionalInfo.description : ''],
            defaultDashboardId: [entity && entity.additionalInfo ? entity.additionalInfo.defaultDashboardId : null],
            defaultDashboardFullscreen: [entity && entity.additionalInfo ? entity.additionalInfo.defaultDashboardFullscreen : false],
            homeDashboardId: [entity && entity.additionalInfo ? entity.additionalInfo.homeDashboardId : null],
            homeDashboardHideToolbar: [entity && entity.additionalInfo &&
            isDefinedAndNotNull(entity.additionalInfo.homeDashboardHideToolbar) ? entity.additionalInfo.homeDashboardHideToolbar : true],
            role: [entity && entity.additionalInfo && entity.additionalInfo.role ? entity.additionalInfo.role : null],
            assignedDashboardIds: [entity && entity.additionalInfo ? entity.additionalInfo.assignedDashboardIds : []]
          }
        )
      }
    );
  }

  updateForm(entity: User) {
    this.selectedRole = entity && entity.additionalInfo && entity.additionalInfo.role ? entity.additionalInfo.role : null;
    this.entityForm.patchValue({email: entity.email});
    this.entityForm.patchValue({firstName: entity.firstName});
    this.entityForm.patchValue({lastName: entity.lastName});
    this.entityForm.patchValue({phone: entity.phone});
    this.entityForm.patchValue({additionalInfo: {description: entity.additionalInfo ? entity.additionalInfo.description : ''}});
    this.entityForm.patchValue({additionalInfo:
        {defaultDashboardId: entity.additionalInfo ? entity.additionalInfo.defaultDashboardId : null}});
    this.entityForm.patchValue({additionalInfo:
        {defaultDashboardFullscreen: entity.additionalInfo ? entity.additionalInfo.defaultDashboardFullscreen : false}});
    this.entityForm.patchValue({additionalInfo:
        {homeDashboardId: entity.additionalInfo ? entity.additionalInfo.homeDashboardId : null}});
    this.entityForm.patchValue({additionalInfo:
        {homeDashboardHideToolbar: entity.additionalInfo &&
          isDefinedAndNotNull(entity.additionalInfo.homeDashboardHideToolbar) ? entity.additionalInfo.homeDashboardHideToolbar : true}});
    this.entityForm.patchValue({additionalInfo:
        {assignedDashboardIds: entity.additionalInfo ? entity.additionalInfo.assignedDashboardIds : []}});
    this.entityForm.patchValue({additionalInfo:
        {role: entity.additionalInfo ? entity.additionalInfo.role : null}});
  }

  onUserIdCopied($event) {
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('user.idCopiedMessage'),
        type: 'success',
        duration: 750,
        verticalPosition: 'bottom',
        horizontalPosition: 'right'
      }
    ));
  }

  compareRoles(option: string, value: string): boolean {
    if (!option || !value) {
      return false;
    }
    return option.toLowerCase() === value.toLowerCase();
  }

  onRoleChanged(event: MatSelectChange): void {
    this.selectedRole = event.value;
    console.log('Selected role:', this.selectedRole);
    // Reset the entity list so any previous selections are cleared.
    if (this.tbEntityList) {
      this.tbEntityList.reset();
    }
  }

  protected readonly Authority = Authority;
  protected readonly UserRole = UserRole;
  protected readonly entityType = EntityType;
}
