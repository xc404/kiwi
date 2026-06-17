/** 项目环境变量元数据（设计器表达式补全用，不含加密项明文） */
export interface ProjectEnvVarMeta {
  key: string;
  description?: string | null;
  encrypted?: boolean | null;
}
