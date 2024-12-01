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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@app/shared/shared.module';
import { AssignToCustomerDialogComponent } from '@modules/home/dialogs/assign-to-customer-dialog.component';
import { AddEntitiesToCustomerDialogComponent } from '@modules/home/dialogs/add-entities-to-customer-dialog.component';
import { HomeDialogsService } from './home-dialogs.service';
import { AddEntitiesToEdgeDialogComponent } from '@home/dialogs/add-entities-to-edge-dialog.component';
import {ReloginDialogComponent} from "@home/dialogs/re-login/relogin-dialog.component";

@NgModule({
  declarations:
  [
    AssignToCustomerDialogComponent,
    AddEntitiesToCustomerDialogComponent,
    AddEntitiesToEdgeDialogComponent,
    ReloginDialogComponent,
  ],
  imports: [
    CommonModule,
    SharedModule
  ],
  exports: [
    AssignToCustomerDialogComponent,
    AddEntitiesToCustomerDialogComponent,
    AddEntitiesToEdgeDialogComponent,
    ReloginDialogComponent,
  ],
  providers: [
    HomeDialogsService
  ]
})
export class HomeDialogsModule { }
