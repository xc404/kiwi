/**
 * 与后端 {@link com.kiwi.project.bpm.model.BpmProcess} 及基类
 * {@link com.kiwi.common.entity.BaseEntity} JSON 序列化字段对齐。
 */
export interface BpmProcess {
  id?: string;
  name?: string;
  bpmnXml?: string;
  projectId?: string;
  /** 当前编辑版本号（保存时相对已部署版本递增） */
  version?: number;
  deployedVersion?: number;
  deployedAt?: string | Date;
  deployedProcessDefinitionId?: string;
  /**
   * 该流程在引擎中允许同时存在的运行中实例数上限；null 或 0 表示不限制。
   */
  maxProcessInstances?: number | null;
  createdBy?: string;
  createdTime?: string | Date;
  updatedBy?: string;
  updatedTime?: string | Date;
}
