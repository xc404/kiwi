

export interface Dict {
    code: string,
    name: string
}

export abstract class IDictService {
    abstract getDictValue(groupCode: string, dictCode: any): string;
    abstract getDictGroup(groupCode: string): Dict[];
}

export class DictService extends IDictService {
    dicts: Map<string, Dict[]>;
    constructor(dicts?: any) {
        super();
        this.dicts = dicts || new Map<string, Dict[]>();
    }

    getDictValue(groupCode: string, dictCode: any): string {
        let dict = this.getDictGroup(groupCode).find(d => d.code == dictCode);
        if (dict) {
            return dict.name || dictCode;
        }
        return dictCode;
    }
    getDictGroup(groupCode: string) {
        return this.dicts.get(groupCode) || [];
    }
}



