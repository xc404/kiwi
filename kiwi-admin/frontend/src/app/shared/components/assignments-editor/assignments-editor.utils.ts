export interface AssignmentRow {
  key: string;
  /** 编辑中的值：字面量文本或 `${var}` */
  valueText: string;
}

export function parseAssignments(raw: unknown): AssignmentRow[] {
  if (raw == null || raw === '') {
    return [{ key: '', valueText: '' }];
  }
  let list: unknown[] = [];
  if (typeof raw === 'string') {
    const t = raw.trim();
    if (!t) {
      return [{ key: '', valueText: '' }];
    }
    try {
      const parsed = JSON.parse(t);
      list = Array.isArray(parsed) ? parsed : [];
    } catch {
      return [{ key: '', valueText: t }];
    }
  } else if (Array.isArray(raw)) {
    list = raw;
  } else {
    return [{ key: '', valueText: '' }];
  }
  if (list.length === 0) {
    return [{ key: '', valueText: '' }];
  }
  return list.map((item: any) => ({
    key: String(item?.key ?? ''),
    valueText: formatValueForEdit(item?.value),
  }));
}

export function formatValueForEdit(value: unknown): string {
  if (value === undefined || value === null) {
    return '';
  }
  if (typeof value === 'string') {
    return value;
  }
  return JSON.stringify(value);
}

export function parseValueText(text: string): unknown {
  const t = text.trim();
  if (t === '') {
    return '';
  }
  if (/^\$\{[a-zA-Z0-9_]+\}$/.test(t)) {
    return t;
  }
  if (t === 'true') {
    return true;
  }
  if (t === 'false') {
    return false;
  }
  if (t === 'null') {
    return null;
  }
  if (/^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(t)) {
    const n = Number(t);
    if (!Number.isNaN(n)) {
      return n;
    }
  }
  try {
    return JSON.parse(t);
  } catch {
    return t;
  }
}
