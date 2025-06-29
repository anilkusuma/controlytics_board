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

import { Directive, HostListener } from '@angular/core';

@Directive({
  selector: '[tbNoCopyPaste]'
})
export class NoCopyPasteDirective {

  @HostListener('paste', ['$event'])
  onPaste(event: Event): boolean {
    event.preventDefault();
    return false;
  }

  @HostListener('copy', ['$event'])
  onCopy(event: Event): boolean {
    event.preventDefault();
    return false;
  }

  @HostListener('cut', ['$event'])
  onCut(event: Event): boolean {
    event.preventDefault();
    return false;
  }

  @HostListener('contextmenu', ['$event'])
  onRightClick(event: Event): boolean {
    event.preventDefault();
    return false;
  }
}