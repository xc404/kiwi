import { inject, Injectable, signal } from '@angular/core';

import { AccountService } from '@services/system/account.service';

export interface UserInfo {
  userName: string;
  id: number | string;
  roles: string[];
  permissions: string[];
  /** 与后端 SessionUser 一致 */
  nickName?: string;
  avatar?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UserInfoStoreService {
  $userInfo = signal<UserInfo>({ id: -1, userName: '', roles: [], permissions:[] });

  userService = inject(AccountService);

  hasRole(role?: string){
    if(!role){
      return true;
    }
    return this.$userInfo().roles.includes(role);
  }

  hasPermission(permission?: string){
    if(!permission){
      return true;
    }
    return this.$userInfo().permissions.includes(permission);
  }

}
