import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { BaseHttpService } from '../base-http.service';

/** 与后端 SysUser / PUT /user/update 请求体一致（可编辑的基本资料） */
export interface UserProfileUpdate {
  nickName?: string | null;
  email?: string | null;
  phonenumber?: string | null;
  /** 0 男 1 女 2 未知 */
  sex?: string | null;
  avatar?: string | null;
}

/** 系统用户管理弹窗等场景使用的用户模型（与 SysUser 不必逐字段一致） */
export interface User {
  id: number;
  password: string;
  userName?: string;
  available?: boolean;
  roleName?: string[];
  sex?: 1 | 0;
  telephone?: string;
  mobile?: string | number;
  email?: string;
  lastLoginTime?: Date;
  oldPassword?: string;
  departmentId?: number;
  departmentName?: string;
}

/*
 * 用户修改密码
 * */
export interface UserPsd {
  id: number | string;
  oldPassword: string;
  newPassword: string;
}

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  http = inject(BaseHttpService);

  public editAccount(param: UserProfileUpdate): Observable<void> {
    return this.http.put('/user/update', param, { needSuccessInfo: true });
  }

  public editAccountPsd(param: UserPsd): Observable<void> {
    return this.http.put('/user/psd', param);
  }
}
