import type { Element } from 'bpmn-js/lib/model/Types';
import { assign } from 'min-dash';

import type { ComponentDescription, ComponentsGroup } from '../../flow-elements/component-provider';

/** 与 BpmnModeler 顶层 options 一并传入 diagram-js `config` */
export interface KiwiReplaceComponentConfig {
  getComponentGroups: () => ComponentsGroup[];
  getRecentUsages: () => ComponentDescription[];
  getCurrentComponentId: (element: Element) => string | undefined;
  replace: (element: Element, component: ComponentDescription, event: MouseEvent | undefined) => void;
}

function hasReplaceCandidates(kiwi: KiwiReplaceComponentConfig | undefined, currentComponentId: string | undefined): boolean {
  if (!kiwi || !currentComponentId) {
    return false;
  }
  const recent = kiwi.getRecentUsages?.() ?? [];
  if (recent.some(c => !!c?.id)) {
    return true;
  }
  const groups = kiwi.getComponentGroups?.() ?? [];
  return groups.some(g => (g.components ?? []).some(c => !!c?.id));
}

function canReplaceComponent(element: Element, kiwi: KiwiReplaceComponentConfig | undefined): boolean {
  if (!kiwi) {
    return false;
  }
  if ((element as { type?: string }).type === 'label') {
    return false;
  }
  if ((element as { type?: string }).type !== 'bpmn:ServiceTask') {
    return false;
  }
  const currentId = kiwi.getCurrentComponentId(element);
  if (!currentId) {
    return false;
  }
  return hasReplaceCandidates(kiwi, currentId);
}

interface PopupProviderThis {
  _kiwi?: KiwiReplaceComponentConfig;
  _translate: (s: string) => string;
}

export function KiwiReplaceComponentPopupProvider(
  this: PopupProviderThis,
  config: { kiwiReplaceComponent?: KiwiReplaceComponentConfig },
  popupMenu: { registerProvider: (id: string, provider: unknown) => void },
  translate: (s: string) => string
) {
  this._kiwi = config.kiwiReplaceComponent;
  this._translate = translate;
  popupMenu.registerProvider('kiwi-replace-component', this);
}

KiwiReplaceComponentPopupProvider.$inject = ['config', 'popupMenu', 'translate'];

KiwiReplaceComponentPopupProvider.prototype.getPopupMenuEntries = function (this: PopupProviderThis, element: Element) {
  const kiwi = this._kiwi;
  const t = this._translate;
  const entries: Record<string, unknown> = {};
  if (!kiwi?.getComponentGroups || !kiwi.getRecentUsages || !kiwi.getCurrentComponentId) {
    return entries;
  }

  const currentId = kiwi.getCurrentComponentId(element);

  const recent = kiwi.getRecentUsages() ?? [];
  for (const c of recent) {
    if (!c?.id || c.id === currentId) {
      continue;
    }
    const id = `kiwi-replace-recent-${String(c.id).replace(/[^a-zA-Z0-9_-]/g, '_')}`;
    entries[id] = {
      label: c.name,
      description: t('最近使用'),
      className: c.icon || 'bpmn-icon-service-task',
      group: { id: '__recent__', name: t('最近使用') },
      action: () => {
        kiwi.replace(element, c, undefined);
      }
    };
  }

  const groups = kiwi.getComponentGroups() || [];
  for (const g of groups) {
    for (const c of g.components || []) {
      if (!c?.id) {
        continue;
      }
      const isCurrent = c.id === currentId;
      const id = `kiwi-replace-comp-${String(c.id).replace(/[^a-zA-Z0-9_-]/g, '_')}`;
      entries[id] = {
        label: c.name,
        description: isCurrent ? t('当前组件 · 刷新参数') : c.descrition,
        className: c.icon || 'bpmn-icon-service-task',
        group: { id: g.group, name: g.group },
        action: () => {
          kiwi.replace(element, c, undefined);
        }
      };
    }
  }
  return entries;
};

interface ContextPadProviderThis {
  _kiwi?: KiwiReplaceComponentConfig;
  _contextPad: { getPad: (el: Element) => { html: HTMLElement } };
  _popupMenu: { isEmpty: (target: Element, id: string) => boolean; open: (...args: unknown[]) => void };
  _translate: (s: string) => string;
}

export function KiwiReplaceComponentContextPadProvider(
  this: ContextPadProviderThis,
  config: { kiwiReplaceComponent?: KiwiReplaceComponentConfig },
  contextPad: ContextPadProviderThis['_contextPad'] & { registerProvider: (p: unknown) => void },
  popupMenu: ContextPadProviderThis['_popupMenu'],
  translate: (s: string) => string
) {
  this._kiwi = config.kiwiReplaceComponent;
  this._contextPad = contextPad;
  this._popupMenu = popupMenu;
  this._translate = translate;
  contextPad.registerProvider(this);
}

KiwiReplaceComponentContextPadProvider.$inject = ['config', 'contextPad', 'popupMenu', 'translate'];

KiwiReplaceComponentContextPadProvider.prototype.getContextPadEntries = function (this: ContextPadProviderThis, element: Element) {
  const kiwi = this._kiwi;
  const contextPad = this._contextPad;
  const popupMenu = this._popupMenu;
  const translate = this._translate;
  const actions: Record<string, unknown> = {};

  if (!canReplaceComponent(element, kiwi)) {
    return actions;
  }

  if (popupMenu.isEmpty(element, 'kiwi-replace-component')) {
    return actions;
  }

  function getMenuPosition(el: Element) {
    const Y_OFFSET = 5;
    const pad = contextPad.getPad(el).html;
    const padRect = pad.getBoundingClientRect();
    return {
      x: padRect.left,
      y: padRect.bottom + Y_OFFSET
    };
  }

  assign(actions, {
    'replace-component': {
      group: 'model',
      className: 'bpmn-icon-screw-wrench',
      title: translate('替换组件'),
      action: {
        click: (event: MouseEvent, el: Element) => {
          const position = assign(getMenuPosition(el), {
            cursor: { x: event.x, y: event.y }
          });
          popupMenu.open(el, 'kiwi-replace-component', position, {
            title: translate('选择要替换的业务组件'),
            width: 320,
            search: true
          });
        }
      }
    }
  });

  return actions;
};

const replaceComponentModule = {
  __init__: ['kiwiReplaceComponentPopupProvider', 'kiwiReplaceComponentContextPadProvider'],
  kiwiReplaceComponentPopupProvider: ['type', KiwiReplaceComponentPopupProvider],
  kiwiReplaceComponentContextPadProvider: ['type', KiwiReplaceComponentContextPadProvider]
};

export default replaceComponentModule;
