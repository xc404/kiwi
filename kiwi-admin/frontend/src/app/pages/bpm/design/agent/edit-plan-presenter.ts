import { ComponentProvider } from '../../flow-elements/component-provider';

export interface PlanStepView {
  index: number;
  kind: 'add' | 'update' | 'remove' | 'connect' | 'meta' | string;
  title: string;
  detail?: string;
  targetRef?: string;
}

export interface PlanDisplayView {
  summary: string;
  steps: PlanStepView[];
  operationCount: number;
}

interface EditPlanJson {
  summary?: string;
  processId?: string;
  operations?: EditOperationJson[];
}

interface EditOperationJson {
  op?: string;
  node?: NodeSpecJson;
  nodeId?: string;
  patch?: NodeSpecJson;
  flow?: FlowSpecJson;
  flowId?: string;
  afterRef?: string;
  beforeRef?: string;
  name?: string;
}

interface NodeSpecJson {
  id?: string;
  type?: string;
  name?: string;
  componentId?: string;
  parameters?: Record<string, unknown>;
}

interface FlowSpecJson {
  id?: string;
  sourceRef?: string;
  targetRef?: string;
  condition?: string;
}

const NodeTypeLabels: Record<string, string> = {
  startEvent: '开始事件',
  endEvent: '结束事件',
  serviceTask: '服务任务',
  userTask: '用户任务',
  exclusiveGateway: '排他网关',
  parallelGateway: '并行网关'
};

const StepKindIcons: Record<string, string> = {
  add: 'plus-circle',
  update: 'edit',
  remove: 'delete',
  connect: 'arrow-right',
  meta: 'info-circle'
};

export function stepKindIcon(kind: string): string {
  return StepKindIcons[kind] ?? 'unordered-list';
}

/** 解析 planDisplayJson；缺失时从 editPlanJson 本地 fallback。 */
export function resolvePlanDisplay(
  planDisplayJson: string | undefined,
  editPlanJson: string | undefined,
  assistantReply: string | undefined,
  componentProvider?: ComponentProvider
): PlanDisplayView | null {
  if (planDisplayJson) {
    try {
      return JSON.parse(planDisplayJson) as PlanDisplayView;
    } catch {
      /* fallback below */
    }
  }
  if (!editPlanJson) {
    return assistantReply
      ? { summary: assistantReply, steps: [], operationCount: 0 }
      : null;
  }
  try {
    const plan = JSON.parse(editPlanJson) as EditPlanJson;
    return presentEditPlan(plan, assistantReply, componentProvider);
  } catch {
    return assistantReply
      ? { summary: assistantReply, steps: [], operationCount: 0 }
      : null;
  }
}

function presentEditPlan(
  plan: EditPlanJson,
  assistantReply: string | undefined,
  componentProvider?: ComponentProvider
): PlanDisplayView {
  const operations = plan.operations ?? [];
  const nameById = buildNameIndex(plan, componentProvider);
  const steps: PlanStepView[] = [];
  let index = 1;
  for (const op of operations) {
    const step = toStep(op, index++, nameById, componentProvider);
    if (step) {
      steps.push(step);
    }
  }
  return {
    summary: resolveSummary(plan, assistantReply, operations.length),
    steps,
    operationCount: operations.length
  };
}

function resolveSummary(plan: EditPlanJson, assistantReply: string | undefined, count: number): string {
  if (plan.summary?.trim()) {
    return plan.summary.trim();
  }
  if (assistantReply?.trim()) {
    return assistantReply.trim();
  }
  if (count === 0) {
    return '暂无具体变更步骤，请补充说明或拒绝后重试。';
  }
  return `将对当前流程执行 ${count} 项变更，请确认后执行。`;
}

function buildNameIndex(plan: EditPlanJson, componentProvider?: ComponentProvider): Record<string, string> {
  const nameById: Record<string, string> = {};
  for (const op of plan.operations ?? []) {
    if (op.node?.id) {
      registerNode(nameById, op.node, componentProvider);
    }
    if (op.patch?.name && op.nodeId) {
      nameById[op.nodeId] = op.patch.name;
    }
  }
  return nameById;
}

function registerNode(
  nameById: Record<string, string>,
  node: NodeSpecJson,
  _componentProvider?: ComponentProvider
): void {
  if (!node.id) {
    return;
  }
  if (node.name) {
    nameById[node.id] = node.name;
  } else {
    nameById[node.id] ??= defaultNodeLabel(node.type, node.id);
  }
}

function toStep(
  op: EditOperationJson,
  index: number,
  nameById: Record<string, string>,
  componentProvider?: ComponentProvider
): PlanStepView | null {
  if (!op.op) {
    return null;
  }
  switch (op.op) {
    case 'addNode':
      return addNodeStep(op, index, nameById, componentProvider);
    case 'updateNode':
      return updateNodeStep(op, index, nameById, componentProvider);
    case 'removeNode':
      return removeNodeStep(op, index, nameById);
    case 'addFlow':
      return addFlowStep(op, index, nameById);
    case 'removeFlow':
      return removeFlowStep(op, index);
    case 'setProcessMeta':
      return metaStep(op, index);
    default:
      return { index, kind: 'meta', title: `执行操作：${op.op}` };
  }
}

function addNodeStep(
  op: EditOperationJson,
  index: number,
  nameById: Record<string, string>,
  componentProvider?: ComponentProvider
): PlanStepView {
  const node = op.node;
  const typeLabel = nodeTypeLabel(node?.type);
  const displayName = nodeDisplayName(node, nameById);
  const detailParts: string[] = [];
  if (op.afterRef) {
    detailParts.push(`接在「${resolveRefLabel(op.afterRef, nameById)}」之后`);
  } else if (op.beforeRef) {
    detailParts.push(`接在「${resolveRefLabel(op.beforeRef, nameById)}」之前`);
  }
  if (node?.componentId) {
    detailParts.push(`使用组件「${componentLabel(node.componentId, componentProvider)}」`);
  }
  return {
    index,
    kind: 'add',
    title: `添加${typeLabel}「${displayName}」`,
    detail: detailParts.length ? detailParts.join('；') : undefined,
    targetRef: op.afterRef ?? op.beforeRef
  };
}

function updateNodeStep(
  op: EditOperationJson,
  index: number,
  nameById: Record<string, string>,
  componentProvider?: ComponentProvider
): PlanStepView {
  const nodeId = op.nodeId ?? op.node?.id;
  return {
    index,
    kind: 'update',
    title: `修改「${resolveRefLabel(nodeId, nameById)}」`,
    detail: describePatch(op.patch, componentProvider),
    targetRef: nodeId
  };
}

function removeNodeStep(op: EditOperationJson, index: number, nameById: Record<string, string>): PlanStepView {
  return {
    index,
    kind: 'remove',
    title: `删除「${resolveRefLabel(op.nodeId, nameById)}」`,
    targetRef: op.nodeId
  };
}

function addFlowStep(op: EditOperationJson, index: number, nameById: Record<string, string>): PlanStepView {
  const flow = op.flow;
  if (!flow) {
    return { index, kind: 'connect', title: '添加连线' };
  }
  return {
    index,
    kind: 'connect',
    title: `连接「${resolveRefLabel(flow.sourceRef, nameById)}」→「${resolveRefLabel(flow.targetRef, nameById)}」`,
    detail: flow.condition ? `条件：${abbreviate(flow.condition, 80)}` : undefined
  };
}

function removeFlowStep(op: EditOperationJson, index: number): PlanStepView {
  return {
    index,
    kind: 'remove',
    title: '删除连线',
    detail: op.flowId ? `连线 id：${shortId(op.flowId)}` : undefined,
    targetRef: op.flowId
  };
}

function metaStep(op: EditOperationJson, index: number): PlanStepView {
  return {
    index,
    kind: 'meta',
    title: `将流程名称改为「${op.name ?? '新流程'}」`
  };
}

function describePatch(patch: NodeSpecJson | undefined, componentProvider?: ComponentProvider): string {
  if (!patch) {
    return '更新节点属性';
  }
  const parts: string[] = [];
  if (patch.name) {
    parts.push(`名称改为「${patch.name}」`);
  }
  if (patch.componentId) {
    parts.push(`组件改为「${componentLabel(patch.componentId, componentProvider)}」`);
  }
  if (patch.parameters && Object.keys(patch.parameters).length) {
    const params = Object.entries(patch.parameters)
      .slice(0, 4)
      .map(([k, v]) => `${k}=${abbreviate(String(v), 24)}`)
      .join('，');
    parts.push(`参数：${params}`);
  }
  return parts.length ? parts.join('；') : '更新节点属性';
}

function resolveRefLabel(id: string | undefined, nameById: Record<string, string>): string {
  if (!id) {
    return '未指定节点';
  }
  return nameById[id] ?? defaultNodeLabel(undefined, id);
}

function nodeDisplayName(node: NodeSpecJson | undefined, nameById: Record<string, string>): string {
  if (!node) {
    return '新节点';
  }
  if (node.name) {
    return node.name;
  }
  if (node.id) {
    return nameById[node.id] ?? defaultNodeLabel(node.type, node.id);
  }
  return defaultNodeLabel(node.type, undefined);
}

function defaultNodeLabel(type: string | undefined, id: string | undefined): string {
  if (type) {
    const label = nodeTypeLabel(type);
    return id ? `${label} ${shortId(id)}` : label;
  }
  if (id) {
    const lower = id.toLowerCase();
    if (lower.includes('start')) {
      return '开始';
    }
    if (lower.includes('end')) {
      return '结束';
    }
    return `节点 ${shortId(id)}`;
  }
  return '节点';
}

function nodeTypeLabel(type: string | undefined): string {
  if (!type) {
    return '节点';
  }
  return NodeTypeLabels[type] ?? type;
}

function componentLabel(componentId: string, componentProvider?: ComponentProvider): string {
  const comp = componentProvider?.getComponent(componentId);
  return comp?.name ?? componentId;
}

function shortId(id: string): string {
  return id.length <= 12 ? id : `${id.slice(0, 12)}…`;
}

function abbreviate(text: string, max: number): string {
  const t = text.trim();
  return t.length <= max ? t : `${t.slice(0, max)}…`;
}
