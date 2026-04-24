import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { PageInfo, SearchCommonVO } from '../../types';
import { BaseHttpService } from '../base-http.service';

/*
 * 用户管理
 * */
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

/** 与后端 {@code UserAccountCtl.IntegrationApiTokenVo} 对齐 */
export interface IntegrationApiToken {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
}

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  http = inject(BaseHttpService);

  public editAccount(param: User): Observable<void> {
    return this.http.put('/user/update', param, { needSuccessInfo: true });
  }

  public editAccountPsd(param: UserPsd): Observable<void> {
    return this.http.put('/user/psd', param);
  }

  /** 签发 cryoEMS 等使用的长期 Bearer Token（会轮换同终端旧令牌） */
  public issueIntegrationApiToken(): Observable<IntegrationApiToken> {
    return this.http.post<IntegrationApiToken>('/user/integration-api-token', {}, { showLoading: true });
  }
}
