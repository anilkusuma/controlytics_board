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

import {ChangeDetectionStrategy, Component, Input, OnDestroy, OnInit} from '@angular/core';
import {User} from '@shared/models/user.model';
import {Authority} from '@shared/models/authority.enum';
import {select, Store} from '@ngrx/store';
import {AppState} from '@core/core.state';
import {selectAuthUser, selectUserDetails} from '@core/auth/auth.selectors';
import {map, switchMap} from 'rxjs/operators';
import {firstValueFrom, of} from 'rxjs';
import {AuthService} from '@core/auth/auth.service';
import {Router} from '@angular/router';
import {AttributeService} from "@core/http/attribute.service";
import {AttributeScope} from "@shared/models/telemetry/telemetry.models";

@Component({
selector: 'tb-user-menu',
  templateUrl: './user-menu.component.html',
  styleUrls: ['./user-menu.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class UserMenuComponent implements OnInit, OnDestroy {

  @Input() displayUserInfo: boolean;

  authorities = Authority;

  authority$ = this.store.pipe(
    select(selectAuthUser),
    map((authUser) => authUser ? authUser.authority : Authority.ANONYMOUS)
  );

  authorityName$ = this.store.pipe(
    select(selectUserDetails),
    switchMap(user => {
      if (!user) {
        return of(null);
      }
      return this.attributeService.getEntityAttributes(user.id, AttributeScope.SERVER_SCOPE, ['role_display_name']).pipe(
        map(data => {
          const role = data.find(d => d.key === 'role_display_name')?.value;
          if (role) {
            return role;
          }
          // fallback to default authority logic
          const authority = user.authority;
          switch (authority) {
            case Authority.SYS_ADMIN:
              return 'user.sys-admin';
            case Authority.TENANT_ADMIN:
              return 'user.admin';
            case Authority.CUSTOMER_USER:
              return 'user.user';
            default:
              return null;
          }
        })
      );
    })
  );

  userDisplayName$ = this.store.pipe(
    select(selectUserDetails),
    map((user) => this.getUserDisplayName(user))
  );

  constructor(private store: Store<AppState>,
              private router: Router,
              private authService: AuthService,
              private attributeService: AttributeService) {
  }

  ngOnInit(): void {
  }

  ngOnDestroy(): void {
  }

  getUserDisplayName(user: User): string {
    let name = '';
    if (user) {
      if ((user.firstName && user.firstName.length > 0) ||
        (user.lastName && user.lastName.length > 0)) {
        if (user.firstName) {
          name += user.firstName;
        }
        if (user.lastName) {
          if (name.length > 0) {
            name += ' ';
          }
          name += user.lastName;
        }
      } else {
        name = user.email;
      }
    }
    return name;
  }

  openAccount(): void {
    this.router.navigate(['account']);
  }

  logout(): void {
    this.authService.logout();
  }

}
