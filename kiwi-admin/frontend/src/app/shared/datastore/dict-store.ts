import { BaseHttpService } from '@app/core/services/http/base-http.service';

import { DataStore } from './data-store';
import { DictRecord, normalizeDictRecord } from './model/dict-record';
import { HttpProxy } from './proxy/http-proxy';

export interface DictStoreConfig {
  storeId: string;
  autoLoad?: boolean;
  pageSize?: number;
}

const DefaultDictPageSize = 1000;

/** 字典 Store：HttpProxy + 字典 Model。 */
export class DictStore extends DataStore<DictRecord> {
  constructor(http: BaseHttpService, config: DictStoreConfig) {
    super(new HttpProxy(http, `/common/dict/${config.storeId}`), {
      storeId: config.storeId,
      pageSize: config.pageSize ?? DefaultDictPageSize,
      autoLoad: config.autoLoad,
      appendMode: true
    });
  }

  findByCode(code: string): DictRecord | undefined {
    return this.findRecord('code', code);
  }

  getDisplayName(code: string): string {
    if (code == null || code === '') {
      return '';
    }
    const key = String(code);
    return this.findByCode(key)?.name ?? key;
  }

  getRecords(): DictRecord[] {
    return this.records().map(raw => normalizeDictRecord(raw));
  }
}
