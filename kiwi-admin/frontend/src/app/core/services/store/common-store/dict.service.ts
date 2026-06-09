import { inject, Injectable } from '@angular/core';

import { Dict, DictService, IDictService } from '@app/shared/dict/dict';

import { BaseHttpService } from '../../http/base-http.service';

@Injectable({
  providedIn: 'root'
})
export class HttpDictService implements IDictService {
  http: BaseHttpService = inject(BaseHttpService);
  // dicts: DictGroup[] = [];
  proxy = new DictService();

  public load(): Promise<void> {
    return new Promise<void>(resolve => {
      return this.http.get<any>('/common/dict/groups').subscribe(dicts => {
        const groups = dicts.content;
        groups.forEach((item: any) => {
          this.proxy.dicts.set(item.code, item.dict);
        });
        resolve();
      });
    });
  }

  public getDictGroup(group: string): Dict[] {
    return this.proxy.getDictGroup(group);
  }

  public getDictValue(group: string, itemKey: string): string {
    const key = `${itemKey}`;
    return this.proxy.getDictValue(group, key);
  }
}
