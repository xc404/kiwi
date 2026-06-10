/** 字典项记录，对标 ExtJS Model 字段定义。 */
export interface DictRecord {
  code: string;
  name: string;
}

export function normalizeDictRecord(raw: {
  code?: string;
  name?: string;
  key?: string;
  value?: string;
}): DictRecord {
  return {
    code: String(raw.code ?? raw.key ?? ''),
    name: String(raw.name ?? raw.value ?? '')
  };
}
