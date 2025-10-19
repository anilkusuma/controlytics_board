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

import { Component } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { Router } from '@angular/router';
import {
  AbstractControl,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { JwtSettings, SecuritySettings } from '@shared/models/settings.models';
import { AdminService } from '@core/http/admin.service';
import { HasConfirmForm } from '@core/guards/confirm-on-exit.guard';
import { mergeMap, tap } from 'rxjs/operators';
import { randomAlphanumeric } from '@core/utils';
import { AuthService } from '@core/auth/auth.service';
import { DialogService } from '@core/services/dialog.service';
import { TranslateService } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { ReLoginDialogComponentData } from '@home/dialogs/re-login/relogin-dialog.component';
import { selectAuth } from '@core/auth/auth.selectors';
import { UserRole } from '@shared/models/user.model';
import { select } from '@ngrx/store';

@Component({
  selector: 'tb-security-settings',
  templateUrl: './security-settings.component.html',
  styleUrls: ['./security-settings.component.scss', './settings-card.scss']
})
export class SecuritySettingsComponent extends PageComponent implements HasConfirmForm {

  securitySettingsFormGroup: UntypedFormGroup;
  jwtSecuritySettingsFormGroup: UntypedFormGroup;

  showMainLoadingBar = false;
  isControlyticsAdmin: boolean = false;

  private securitySettings: SecuritySettings;
  private jwtSettings: JwtSettings;

  constructor(protected store: Store<AppState>,
              private router: Router,
              private adminService: AdminService,
              private authService: AuthService,
              private dialogService: DialogService,
              private translate: TranslateService,
              private fb: UntypedFormBuilder) {
    super(store);

    // Check user role
    this.store.pipe(select(selectAuth)).subscribe(auth => {
      const currentUser = auth.userDetails;
      this.isControlyticsAdmin = currentUser.additionalInfo?.role === UserRole.CONTROLYTICS_ADMIN;
    });

    this.buildSecuritySettingsForm();
    this.buildJwtSecuritySettingsForm();
    this.adminService.getSecuritySettings().subscribe(
      securitySettings => this.processSecuritySettings(securitySettings)
    );
    this.adminService.getJwtSettings().subscribe(
      jwtSettings => this.processJwtSettings(jwtSettings)
    );
  }

  buildSecuritySettingsForm() {
    this.securitySettingsFormGroup = this.fb.group({
      maxFailedLoginAttempts: [null, [Validators.min(0)]],
      userLockoutNotificationEmail: ['', []],
      passwordPolicy: this.fb.group(
        {
          minimumLength: [null, [Validators.required, Validators.min(6), Validators.max(50)]],
          maximumLength: [null, [Validators.min(6), this.maxPasswordValidation()]],
          minimumUppercaseLetters: [null, Validators.min(0)],
          minimumLowercaseLetters: [null, Validators.min(0)],
          minimumDigits: [null, Validators.min(0)],
          minimumSpecialCharacters: [null, Validators.min(0)],
          passwordExpirationPeriodDays: [null, Validators.min(0)],
          passwordReuseFrequencyDays: [null, Validators.min(0)],
          allowWhitespaces: [true],
          forceUserToResetPasswordIfNotValid: [false]
        }
      )
    });
  }

  buildJwtSecuritySettingsForm() {
    this.jwtSecuritySettingsFormGroup = this.fb.group({
      tokenIssuer: ['thingsboardDefaultIssuer', [Validators.required]],
      tokenSigningKey: ['thingsboardDefaultSigningKey', [Validators.required, this.base64Format.bind(this)]],
      tokenExpirationTime: [9000, [Validators.required, Validators.pattern('[0-9]*'), Validators.min(60)]],
      refreshTokenExpTime: [0, [Validators.required, Validators.pattern('[0-9]*'), Validators.min(900)]]
    }, { validators: this.refreshTokenTimeGreatTokenTime.bind(this) });
  }

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

  private performSaveSecuritySettings(): void {
    this.securitySettings = {...this.securitySettings, ...this.securitySettingsFormGroup.value};
    this.adminService.saveSecuritySettings(this.securitySettings).subscribe(
      securitySettings => this.processSecuritySettings(securitySettings)
    );
  }

  saveJwtSettings() {
    this.store.pipe(select(selectAuth)).subscribe(auth => {
      const currentUser = auth.userDetails;
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
              this.performSaveJwtSettings();
            }
          }
        );
      } else {
        // Skip re-authentication for Controlytics Admin or other roles
        this.performSaveJwtSettings();
      }
    });
  }

  private performSaveJwtSettings(): void {
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

  private maxPasswordValidation(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value: string = control.value;
      if (value) {
        if (value < control.parent.value?.minimumLength) {
          return {lessMin: true};
        }
      }
      return null;
    };
  }

  discardSetting() {
    this.securitySettingsFormGroup.reset(this.securitySettings);
  }

  discardJwtSetting() {
    this.jwtSecuritySettingsFormGroup.reset(this.jwtSettings);
  }

  markAsTouched() {
    this.jwtSecuritySettingsFormGroup.get('tokenSigningKey').markAsTouched();
  }

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

  generateSigningKey() {
    this.jwtSecuritySettingsFormGroup.get('tokenSigningKey').setValue(btoa(randomAlphanumeric(64)));
    if (this.jwtSecuritySettingsFormGroup.get('tokenSigningKey').pristine) {
      this.jwtSecuritySettingsFormGroup.get('tokenSigningKey').markAsDirty();
      this.jwtSecuritySettingsFormGroup.get('tokenSigningKey').markAsTouched();
    }
  }

  private processSecuritySettings(securitySettings: SecuritySettings) {
    this.securitySettings = securitySettings;
    this.securitySettingsFormGroup.reset(this.securitySettings);
  }

  private processJwtSettings(jwtSettings: JwtSettings) {
    this.jwtSettings = jwtSettings;
    this.jwtSecuritySettingsFormGroup.reset(jwtSettings);
  }

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

  private base64Format(control: UntypedFormControl): { [key: string]: boolean } | null {
    if (control.value === '' || control.value === 'thingsboardDefaultSigningKey') {
      return null;
    }
    try {
      const value = atob(control.value);
      if (value.length < 64) {
        return {minLength: true};
      }
      return null;
    } catch (e) {
      return {base64: true};
    }
  }

  confirmForm(): UntypedFormGroup {
    return this.securitySettingsFormGroup.dirty ? this.securitySettingsFormGroup : this.jwtSecuritySettingsFormGroup;
  }
}
