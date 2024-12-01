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

import {EntityType} from "@shared/models/entity-type.models";
import {Component, Inject, OnInit, SkipSelf} from "@angular/core";
import {ErrorStateMatcher} from "@angular/material/core";
import {DialogComponent} from "@shared/components/dialog.component";
import {Store} from "@ngrx/store";
import {AppState} from "@core/core.state";
import {Router} from "@angular/router";
import {MAT_DIALOG_DATA, MatDialogRef} from "@angular/material/dialog";
import {
  AbstractControl,
  FormGroupDirective,
  NgForm,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup, Validators
} from "@angular/forms";
import {getCurrentAuthState, getCurrentAuthUser} from "@core/auth/auth.selectors";
import {AuthService} from "@core/auth/auth.service";
import {NotificationService} from "@core/http/notification.service";
import {ToastNotificationService} from "@core/services/toast-notification.service";
import {NotificationType} from "@shared/models/notification.models";
import {HistoryWindowType, TimewindowType} from "@shared/models/time/time.models";
import {IntervalType} from "@shared/models/telemetry/telemetry.models";


export interface ReLoginDialogComponentData {
  customerId: string;
  entityType: EntityType;
  remarksRequired: boolean;
  timeRangeRequired: boolean;
  intervalRequired: boolean;
}

export interface ReLoginDialogComponentResponse {
  reloginStatus: boolean;
  startTimeInMs?: number;
  endTimeInMs?: number;
  interval?: number;
  remarks: string;
}

@Component({
  selector: 'tb-relogin-dialog-component',
  templateUrl: "./relogin-dialog.component.html",
  providers: [{provide: ErrorStateMatcher, useExisting: ReloginDialogComponent}],
  styleUrls: []
})
export class ReloginDialogComponent extends DialogComponent<ReloginDialogComponent, ReLoginDialogComponentResponse> implements OnInit, ErrorStateMatcher {

  reloginFormGroup: UntypedFormGroup;
  submitted = false;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              private authService: AuthService,
              private notificationService: ToastNotificationService,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<ReloginDialogComponent, ReLoginDialogComponentResponse>,
              public fb: UntypedFormBuilder,
              @Inject(MAT_DIALOG_DATA) public data: ReLoginDialogComponentData) {
    super(store, router, dialogRef);
  }

  isErrorState(control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  ngOnInit(): void {
    this.reloginFormGroup = this.fb.group({
      username: ['', [Validators.required]],
      password: ['', [Validators.required]],
    });
    if (this.data.remarksRequired) {
      this.reloginFormGroup.addControl('remarks', this.fb.control('', [Validators.required, Validators.minLength(2)]));
    }
    if (this.data.timeRangeRequired) {
      this.reloginFormGroup.addControl('fixedTimeWindow', this.fb.control('', [Validators.required]));
    }
    if (this.data.intervalRequired) {
      this.reloginFormGroup.addControl('aggregationInterval', this.fb.control('', [Validators.required]));
    }
  }

  login() {
    this.submitted = true;
    if (this.reloginFormGroup.valid) {
      console.log(this.reloginFormGroup.get('fixedTimeWindow')?.value);
      console.log(this.reloginFormGroup.get('aggregationInterval')?.value);

      if (this.reloginFormGroup.get('username').value !== getCurrentAuthState(this.store).userDetails.email) {
        this.notificationService.dispatchNotification({
          message: "Invalid Login ID. Login with active user",
          type: 'error',
          duration: 5000,
        });
        return;
      }
      this.authService.relogin({
        username: this.reloginFormGroup.get('username').value,
        password: this.reloginFormGroup.get('password').value
      }).subscribe(
        (response) => {
          if (response.token !== null) {
            this.dialogRef.close({
              reloginStatus: true,
              remarks: this.data.remarksRequired ? this.reloginFormGroup.get('remarks').value : '',
              startTimeInMs: this.data.timeRangeRequired ? this.reloginFormGroup.get('fixedTimeWindow').value.startTimeMs : null,
              endTimeInMs: this.data.timeRangeRequired ? this.reloginFormGroup.get('fixedTimeWindow').value.endTimeMs : null,
              interval: this.data.intervalRequired ? this.reloginFormGroup.get('aggregationInterval').value : null
            });
          } else {
            this.dialogRef.close({reloginStatus: false, remarks: 'Invalid credentials'});
          }
        }
      );
    }
  }

  cancel() {
    this.dialogRef.close(null);
  }

  protected readonly timewindowTypes = TimewindowType;
  protected readonly historyTypes = HistoryWindowType;
  protected readonly IntervalType = IntervalType;
  protected readonly Number = Number;
}
