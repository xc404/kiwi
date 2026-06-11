import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';

import { CamundaHistoricActivityInstance } from '../service/process-instance.service';

export const CALL_ACTIVITY_LINK_OVERLAY_TYPE = 'call-activity-instance-link';

interface DiagramElement {
  id: string;
  type?: string;
  businessObject?: { id?: string };
}

interface OverlaysService {
  remove: (filter: { type?: string }) => void;
  add: (
    element: DiagramElement | string,
    type: string,
    attrs: {
      position: { top?: number; right?: number; bottom?: number; left?: number };
      html: HTMLElement;
    }
  ) => string;
}

/**
 * CallActivity 历史活动 -> 子流程实例 ID（同一 activityId 多条时与 buildActivityStateMap 策略一致）。
 */
export function buildCalledProcessInstanceMap(activities: CamundaHistoricActivityInstance[]): Map<string, string> {
  const byActivityId = new Map<string, CamundaHistoricActivityInstance>();

  for (const activity of activities) {
    const type = activity.activityType?.trim().toLowerCase();
    if (type !== 'callactivity') {
      continue;
    }
    const calledId = readCalledProcessInstanceId(activity);
    if (!calledId) {
      continue;
    }
    const activityId = activity.activityId?.trim();
    if (!activityId) {
      continue;
    }

    const existing = byActivityId.get(activityId);
    if (!existing) {
      byActivityId.set(activityId, activity);
      continue;
    }
    if (existing.completed && !activity.completed) {
      byActivityId.set(activityId, activity);
      continue;
    }
    if (!existing.completed && activity.completed) {
      continue;
    }
    const existingStart = historicActivityStartMs(existing);
    const nextStart = historicActivityStartMs(activity);
    if (nextStart >= existingStart) {
      byActivityId.set(activityId, activity);
    }
  }

  const out = new Map<string, string>();
  for (const [activityId, activity] of byActivityId) {
    const calledId = readCalledProcessInstanceId(activity);
    if (calledId) {
      out.set(activityId, calledId);
    }
  }
  return out;
}

function readCalledProcessInstanceId(activity: CamundaHistoricActivityInstance): string | undefined {
  const raw = activity.calledProcessInstanceId;
  if (raw == null) {
    return undefined;
  }
  const id = String(raw).trim();
  return id || undefined;
}

function historicActivityStartMs(activity: CamundaHistoricActivityInstance): number {
  const raw = activity.startTime;
  if (raw == null || raw === '') {
    return 0;
  }
  const t = Date.parse(String(raw));
  return Number.isNaN(t) ? 0 : t;
}

/**
 * 在 CallActivity 节点右上角叠加「查看子流程实例」链接（仅当存在 calledProcessInstanceId 时）。
 */
export function syncCallActivityLinkOverlays(viewer: NavigatedViewer, calledMap: Map<string, string>, openChildInstance: (childProcessInstanceId: string) => void): void {
  const overlays = viewer.get('overlays') as OverlaysService | undefined;
  const elementRegistry = viewer.get('elementRegistry') as
    | {
        getAll: () => DiagramElement[];
        get: (id: string) => DiagramElement | undefined;
      }
    | undefined;

  if (!overlays || !elementRegistry) {
    return;
  }

  overlays.remove({ type: CALL_ACTIVITY_LINK_OVERLAY_TYPE });

  if (!calledMap.size) {
    return;
  }

  const all = elementRegistry.getAll();
  for (const el of all) {
    if (el.type !== 'bpmn:CallActivity') {
      continue;
    }
    const activityId = el.businessObject?.id?.trim() || el.id?.trim();
    if (!activityId) {
      continue;
    }
    const childId = calledMap.get(activityId);
    if (!childId) {
      continue;
    }

    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'kiwi-bpmn-subprocess-link';
    button.title = '查看子流程实例';
    button.setAttribute('aria-label', '查看子流程实例');
    button.innerHTML =
      '<span class="kiwi-bpmn-subprocess-link__icon" aria-hidden="true">' +
      '<svg viewBox="64 64 896 896" focusable="false" width="1em" height="1em" fill="currentColor">' +
      '<path d="M574 665.4a8 8 0 00-8 8H488.1c-4.4 0-8-3.6-8-8V548.1c0-4.4 3.6-8 8-8h77.3c4.4 0 8 3.6 8 8v109.3z"/>' +
      '<path d="M854.6 288.6L639.4 73.4c-48.4-48.4-127.1-48.4-175.5 0l-71.9 71.9c-48.4 48.4-48.4 127.1 0 175.5l53.3 53.3c7.8 7.8 20.5 7.8 28.3 0l81-81c7.8-7.8 7.8-20.5 0-28.3l-53.3-53.3c-17.1-17.1-17.1-45 0-62.1l43.4-43.4c17.1-17.1 45-17.1 62.1 0l215.2 215.2c17.1 17.1 17.1 45 0 62.1l-43.4 43.4c-7.8 7.8-7.8 20.5 0 28.3l81 81c7.8 7.8 20.5 7.8 28.3 0l53.3-53.3c48.5-48.5 48.5-127.2 0-175.5z"/>' +
      '</svg></span>';

    button.addEventListener('click', ev => {
      ev.preventDefault();
      ev.stopPropagation();
      openChildInstance(childId);
    });
    button.addEventListener('mousedown', ev => {
      ev.stopPropagation();
    });

    overlays.add(el, CALL_ACTIVITY_LINK_OVERLAY_TYPE, {
      position: { top: -8, right: 4 },
      html: button
    });
  }
}

export function clearCallActivityLinkOverlays(viewer: NavigatedViewer | undefined): void {
  if (!viewer) {
    return;
  }
  const overlays = viewer.get('overlays') as OverlaysService | undefined;
  overlays?.remove({ type: CALL_ACTIVITY_LINK_OVERLAY_TYPE });
}
