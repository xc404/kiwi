import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';
import { ComponentService } from '../../component/component-service';
import { ElementModel } from '../extension/element-model';
/** 与后端 BpmProcessIoAnalysisService.INPUT_VAR_REF 一致 */
export const BPM_INPUT_VAR_REF = /\$\{([a-zA-Z0-9_]+)\}/g;

export interface SpelVariableSuggestion {
  key: string;
  /** 展示用 */
  name?: string;
  /** 来源说明 */
  source: 'upstream' | 'referenced';
}

function getInputNamespace(element: Element): string {
  return element.type === 'bpmn:CallActivity' ? 'In' : 'inputParameter';
}

function extractVarNames(text: string): string[] {
  if (!text?.trim()) {
    return [];
  }
  const out: string[] = [];
  let m: RegExpExecArray | null;
  const re = new RegExp(BPM_INPUT_VAR_REF.source, 'g');
  while ((m = re.exec(text)) !== null) {
    out.push(m[1]);
  }
  return out;
}

function getSequenceFlowEndpoints(flow: Element): { sourceId: string; targetId: string } | null {
  const bo: any = flow.businessObject;
  const src = bo?.sourceRef;
  const tgt = bo?.targetRef;
  const sid = typeof src === 'string' ? src : src?.id;
  const tid = typeof tgt === 'string' ? tgt : tgt?.id;
  if (!sid || !tid) {
    return null;
  }
  return { sourceId: sid, targetId: tid };
}

/** targetId -> 入边 source id 列表 */
function buildReverseAdjacency(bpmnModeler: BaseViewer): Map<string, string[]> {
  const registry = bpmnModeler.get('elementRegistry') as { getAll: () => Element[] };
  const rev = new Map<string, string[]>();
  for (const el of registry.getAll()) {
    if (el.type !== 'bpmn:SequenceFlow') {
      continue;
    }
    const ep = getSequenceFlowEndpoints(el);
    if (!ep) {
      continue;
    }
    const list = rev.get(ep.targetId) ?? [];
    list.push(ep.sourceId);
    rev.set(ep.targetId, list);
  }
  return rev;
}

/** 沿 sequenceFlow 反向，从 nodeId 可达的所有前驱节点 id（含自身） */
export function backwardReachable(reverseAdj: Map<string, string[]>, nodeId: string): Set<string> {
  const seen = new Set<string>();
  const q: string[] = [nodeId];
  seen.add(nodeId);
  while (q.length) {
    const n = q.pop()!;
    for (const pred of reverseAdj.get(n) ?? []) {
      if (!seen.has(pred)) {
        seen.add(pred);
        q.push(pred);
      }
    }
  }
  return seen;
}

function collectOutputKeysFromComponent(componentService: ComponentService, el: Element): string[] {
  const comp = componentService.getComponentForElement(el);
  if (!comp?.outputParameters?.length) {
    return [];
  }
  const keys: string[] = [];
  for (const p of comp.outputParameters) {
    if ((p as { hidden?: boolean }).hidden) {
      continue;
    }
    const k = (p.key ?? '').trim();
    if (k) {
      keys.push(k);
    }
  }
  return keys;
}

/**
 * 图中所有组件任务/调用活动输入里出现过的 `${var}`（流程中被引用过的变量名）。
 */
export function collectReferencedVariableNames(
  bpmnModeler: BaseViewer,
  elementModel: ElementModel,
  componentService: ComponentService
): string[] {
  const registry = bpmnModeler.get('elementRegistry') as { getAll: () => Element[] };
  const seen = new Set<string>();
  for (const el of registry.getAll()) {
    if (el.type !== 'bpmn:ServiceTask' && el.type !== 'bpmn:CallActivity') {
      continue;
    }
    const comp = componentService.getComponentForElement(el);
    if (!comp?.inputParameters?.length) {
      continue;
    }
    const ns = getInputNamespace(el);
    for (const p of comp.inputParameters) {
      if ((p as { hidden?: boolean }).hidden) {
        continue;
      }
      let nspace = (p.namespace as string) || ns;
      if (el.type === 'bpmn:CallActivity' && nspace === 'inputParameter') {
        nspace = 'In';
      }
      const raw = elementModel.getValue(bpmnModeler as any, el, nspace, p.key);
      if (raw == null) {
        continue;
      }
      for (const v of extractVarNames(String(raw))) {
        seen.add(v);
      }
    }
  }
  return [...seen].sort((a, b) => a.localeCompare(b));
}

/**
 * 当前选中节点上游（控制流可达的前驱）组件产出的输出变量名（outputParameters.key）。
 */
export function collectUpstreamOutputVariableNames(
  bpmnModeler: BaseViewer,
  componentService: ComponentService,
  current: Element
): string[] {
  const rev = buildReverseAdjacency(bpmnModeler);
  const preds = backwardReachable(rev, current.id);
  const keys = new Set<string>();
  for (const pid of preds) {
    if (pid === current.id) {
      continue;
    }
    const reg = bpmnModeler.get('elementRegistry') as { get: (id: string) => Element | undefined };
    const el = reg.get(pid);
    if (!el || (el.type !== 'bpmn:ServiceTask' && el.type !== 'bpmn:CallActivity')) {
      continue;
    }
    for (const k of collectOutputKeysFromComponent(componentService, el)) {
      keys.add(k);
    }
  }
  return [...keys].sort((a, b) => a.localeCompare(b));
}

/**
 * 合并「图中已引用变量」与「上游组件输出变量」，供 SpEL 编辑器 `$` 自动完成。
 */
export function buildSpelVariableSuggestions(
  bpmnModeler: BaseViewer,
  elementModel: ElementModel,
  componentService: ComponentService,
  selectedElement: Element
): SpelVariableSuggestion[] {
  const referenced = new Map<string, SpelVariableSuggestion>();
  for (const key of collectReferencedVariableNames(bpmnModeler, elementModel, componentService)) {
    referenced.set(key, { key, source: 'referenced' });
  }
  const upstream = new Map<string, SpelVariableSuggestion>();
  for (const key of collectUpstreamOutputVariableNames(bpmnModeler, componentService, selectedElement)) {
    upstream.set(key, { key, source: 'upstream' });
  }
  const order: string[] = [];
  const push = (k: string) => {
    if (!order.includes(k)) {
      order.push(k);
    }
  };
  for (const k of upstream.keys()) {
    push(k);
  }
  for (const k of referenced.keys()) {
    push(k);
  }
  return order.map((k) => upstream.get(k) ?? referenced.get(k)!);
}
