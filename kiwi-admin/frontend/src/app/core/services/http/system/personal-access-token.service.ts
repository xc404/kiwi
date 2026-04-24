import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { BaseHttpService } from '../base-http.service';
import { Page } from '@app/shared/components/crud/crud-datastore';

export interface PersonalAccessTokenRow {
  id: string;
  name: string | null;
  tokenHint: string;
  createdTime: string | null;
  expiresAt: string | null;
}

export interface CreatePersonalAccessTokenBody {
  name?: string | null;
}

export interface CreatePersonalAccessTokenResult {
  id: string;
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  expiresAt: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class PersonalAccessTokenService {
  private http = inject(BaseHttpService);

  listMine(): Observable<PersonalAccessTokenRow[]> {
    return this.http.get<any>('/user/personal-access-tokens', undefined, { showLoading: false }).pipe(
      map(res => {
        return res.content;
      })
    );
  }

  create(body: CreatePersonalAccessTokenBody): Observable<CreatePersonalAccessTokenResult> {
    return this.http.post<CreatePersonalAccessTokenResult>('/user/personal-access-tokens', body ?? {}, { showLoading: true });
  }

  revoke(id: string): Observable<void> {
    return this.http.delete<void>(`/user/personal-access-tokens/${id}`, undefined, {
      showLoading: true,
      needSuccessInfo: true
    });
  }
}
