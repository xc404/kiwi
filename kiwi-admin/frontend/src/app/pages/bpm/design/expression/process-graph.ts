import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';

function getSequenceFlowEndpoints(flow: Element): { sourceId: string; targetId: string } | null {
  const bo: { sourceRef?: { id?: string } | string; targetRef?: { id?: string } | string } = flow.businessObject;
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
export function buildReverseAdjacency(bpmnModeler: BaseViewer): Map<string, string[]> {
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
