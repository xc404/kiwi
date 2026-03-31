/** 与后端 MonitorSnapshotDto 对齐；新增 kind 时在此与模板中扩展渲染分支。 */
export interface MonitorSnapshot {
  collectedAt: string;
  modules: MonitorModule[];
}

export interface MonitorModule {
  id: string;
  title: string;
  order: number;
  metrics: MonitorMetric[];
}

export interface MonitorMetric {
  id: string;
  label: string;
  /** percent | number | text | bytes | boolean */
  kind: string;
  value?: number | null;
  valueText?: string | null;
  unit?: string | null;
}
