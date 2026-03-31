import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

// import { MENU_TOKEN } from '@config/menu';
import { Menu } from '@core/services/types';
import { BaseHttpService } from '@services/base-http.service';
import { UserInfo } from '../../store/common-store/userInfo-store.service';
// import { MenusService } from '@services/system/menus.service';

export interface UserLogin {
  userName: string;
  password: string;
}

export interface LoginOutput {
  token: string;
}


@Injectable({
  providedIn: 'root'
})
export class LoginService {
  http = inject(BaseHttpService);
  // private menus = inject(MENU_TOKEN);

  public login(params: UserLogin): Observable<LoginOutput> {
    return this.http.post('/auth/signin', params, { needSuccessInfo: false });
  }

  public loginOut(): Observable<string> {
    return this.http.post('/auth/signout', null, { needSuccessInfo: false });
  }

  public getUserMenus(): Observable<Menu[]> {
    return this.http.get(`/auth/menus`).pipe(map((result: any) => {
      return result.content;
    })
    );
  }

  public getUserInfo(): Observable<UserInfo> {
    return this.http.get('/auth/userinfo');
  }

  public getUserPermissions(): Observable<string[]> {
    return this.http.get('/auth/permissions').pipe(map((result: any) => {
      return result.content;
    }));
  }
}
