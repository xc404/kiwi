import { inject, Injectable } from "@angular/core";
import { BaseHttpService } from "../../http/base-http.service";
import { Dict, DictService, IDictService } from "@app/shared/dict/dict";


@Injectable({
    providedIn: 'root',
})
export class HttpDictService implements IDictService {

    http: BaseHttpService = inject(BaseHttpService);
    // dicts: DictGroup[] = [];
    proxy = new  DictService();

    public load(): Promise<void> {

        return new Promise<void>(resolve => {
            return this.http.get<any>('/common/dict/groups').subscribe(
                dicts => {
                    let groups = dicts.content;
                    groups.forEach((item: any) => {
                        this.proxy.dicts.set(item.code, item.dict);
                    });
                    resolve();
                }
            );
        });
    }

    public getDictGroup(group: string): Dict[] {
        return this.proxy.getDictGroup(group);
    }

    public getDictValue(group: string, itemKey: string): string {
        let key = itemKey + "";
        return this.proxy.getDictValue(group, key);
    }


}