import { inject, Injectable, Type } from '@angular/core';
import { Observable } from 'rxjs';

import { ModalOptions } from 'ng-zorro-antd/modal';

import { ChangePasswordComponent } from './change-password.component';
import { ModalResponse, ModalWrapService } from '../base-modal';

@Injectable({
  providedIn: 'root'
})
export class ChangePasswordService {
  private modalWrapService = inject(ModalWrapService);

  protected getContentComponent(): Type<ChangePasswordComponent> {
    return ChangePasswordComponent;
  }

  public show(modalOptions: ModalOptions = {}, params?: object): Observable<ModalResponse> {
    return this.modalWrapService.show<ChangePasswordComponent, object>(this.getContentComponent(), modalOptions, params);
  }
}
