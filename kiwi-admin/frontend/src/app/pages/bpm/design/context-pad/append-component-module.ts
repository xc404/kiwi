import { assign } from 'min-dash';
import { is } from 'bpmn-js/lib/util/ModelUtil';
import { isEventSubProcess } from 'bpmn-js/lib/util/DiUtil';
import type { Element } from 'bpmn-js/lib/model/Types';
import type { ComponentDescription, ComponentsGroup } from '../../flow-elements/component-provider';

/** 与 BpmnModeler 顶层 options 一并传入 diagram-js `config` */
export interface KiwiAppendComponentConfig {
  getComponentGroups: () => ComponentsGroup[];
  /** 从已保存流程解析的最近使用（与组件库同为 ComponentDescription；快照已合并进参数 defaultValue） */
  getRecentUsages: () => ComponentDescription[];
  append: (sourceElement: Element, component: ComponentDescription, event: MouseEvent | undefined) => void;
}

function isEventType(businessObject: any, type: string, eventDefinitionType: string): boolean {
  const isType = businessObject.$instanceOf(type);
  let isDefinition = false;
  const definitions = businessObject.eventDefinitions || [];
  for (const def of definitions) {
    if (def.$type === eventDefinitionType) {
      isDefinition = true;
    }
  }
  return isType && isDefinition;
}

function canAppendComponent(element: Element): boolean {
  const bo: any = element.businessObject;
  if (!bo) {
    return false;
  }
  if ((element as any).type === 'label') {
    return false;
  }
  if (!is(bo, 'bpmn:FlowNode')) {
    return false;
  }
  if (is(bo, 'bpmn:EndEvent')) {
    return false;
  }
  if (bo.isForCompensation) {
    return false;
  }
  if (isEventSubProcess(element)) {
    return false;
  }
  if (isEventType(bo, 'bpmn:IntermediateThrowEvent', 'bpmn:LinkEventDefinition')) {
    return false;
  }
  return true;
}

function hasAnyAppendEntry(kiwi: KiwiAppendComponentConfig | undefined): boolean {
  if (!kiwi) {
    return false;
  }
  const groups = kiwi.getComponentGroups?.() ?? [];
  if (groups.some((g) => (g.components?.length ?? 0) > 0)) {
    return true;
  }
  const recent = kiwi.getRecentUsages?.() ?? [];
  return recent.some((c) => !!c?.id);
}

interface PopupProviderThis {
  _kiwi?: KiwiAppendComponentConfig;
  _translate: (s: string) => string;
}

/**
 * 弹出菜单：最近使用 + 组件库分组；选中后由 config.kiwiAppendComponent.append 完成追加。
 */
export function KiwiAppendComponentPopupProvider(
  this: PopupProviderThis,
  config: { kiwiAppendComponent?: KiwiAppendComponentConfig },
  popupMenu: { registerProvider: (id: string, provider: unknown) => void },
  translate: (s: string) => string,
) {
  this._kiwi = config.kiwiAppendComponent;
  this._translate = translate;
  popupMenu.registerProvider('kiwi-append-component', this);
}

KiwiAppendComponentPopupProvider.$inject = ['config', 'popupMenu', 'translate'];

KiwiAppendComponentPopupProvider.prototype.getPopupMenuEntries = function (this: PopupProviderThis, element: Element) {
  const kiwi = this._kiwi;
  const t = this._translate;
  const entries: Record<string, any> = {};
  if (!kiwi?.getComponentGroups || !kiwi.getRecentUsages) {
    return entries;
  }

  const recent = kiwi.getRecentUsages() ?? [];
  for (const c of recent) {
    if (!c?.id) {
      continue;
    }
    const id = 'kiwi-recent-' + String(c.id).replace(/[^a-zA-Z0-9_-]/g, '_');
    entries[id] = {
      label: c.name,
      description: t('最近使用'),
      className: c.icon || 'bpmn-icon-service-task',
      group: { id: '__recent__', name: t('最近使用') },
      action: (event: MouseEvent) => {
        kiwi.append(element, c, event);
      },
    };
  }

  const groups = kiwi.getComponentGroups() || [];
  for (const g of groups) {
    for (const c of g.components || []) {
      const id = 'kiwi-comp-' + String(c.id).replace(/[^a-zA-Z0-9_-]/g, '_');
      entries[id] = {
        label: c.name,
        description: c.descrition,
        className: c.icon || 'bpmn-icon-service-task',
        group: { id: g.group, name: g.group },
        action: (event: MouseEvent) => {
          kiwi.append(element, c, event);
        },
      };
    }
  }
  return entries;
};

interface ContextPadProviderThis {
  _kiwi?: KiwiAppendComponentConfig;
  _contextPad: { getPad: (el: Element) => { html: HTMLElement } };
  _popupMenu: { isEmpty: (target: Element, id: string) => boolean; open: (...args: unknown[]) => void };
  _translate: (s: string) => string;
}

/**
 * 上下文菜单：追加业务组件（打开组件列表弹层）。
 */
export function KiwiAppendComponentContextPadProvider(
  this: ContextPadProviderThis,
  config: { kiwiAppendComponent?: KiwiAppendComponentConfig },
  contextPad: ContextPadProviderThis['_contextPad'] & { registerProvider: (p: unknown) => void },
  popupMenu: ContextPadProviderThis['_popupMenu'],
  translate: (s: string) => string,
) {
  this._kiwi = config.kiwiAppendComponent;
  this._contextPad = contextPad;
  this._popupMenu = popupMenu;
  this._translate = translate;
  contextPad.registerProvider(this);
}

KiwiAppendComponentContextPadProvider.$inject = ['config', 'contextPad', 'popupMenu', 'translate'];

KiwiAppendComponentContextPadProvider.prototype.getContextPadEntries = function (
  this: ContextPadProviderThis,
  element: Element,
) {
  const kiwi = this._kiwi;
  const contextPad = this._contextPad;
  const popupMenu = this._popupMenu;
  const translate = this._translate;
  const actions: Record<string, any> = {};

  if (!kiwi || !canAppendComponent(element)) {
    return actions;
  }

  if (!hasAnyAppendEntry(kiwi)) {
    return actions;
  }

  if (popupMenu.isEmpty(element, 'kiwi-append-component')) {
    return actions;
  }

  function getMenuPosition(el: Element) {
    const Y_OFFSET = 5;
    const pad = contextPad.getPad(el).html;
    const padRect = pad.getBoundingClientRect();
    return {
      x: padRect.left,
      y: padRect.bottom + Y_OFFSET,
    };
  }

  assign(actions, {
    'append-component': {
      group: 'model',
      className: 'bpmn-icon-service-task',
      title: translate('追加业务组件'),
      action: {
        click: (event: MouseEvent, el: Element) => {
          const position = assign(getMenuPosition(el), {
            cursor: { x: event.x, y: event.y },
          });
          popupMenu.open(el, 'kiwi-append-component', position, {
            title: translate('选择要追加的业务组件'),
            width: 320,
            search: true,
          });
        },
      },
    },
  });

  return actions;
};

const appendComponentModule = {
  __init__: ['kiwiAppendComponentPopupProvider', 'kiwiAppendComponentContextPadProvider'],
  kiwiAppendComponentPopupProvider: ['type', KiwiAppendComponentPopupProvider],
  kiwiAppendComponentContextPadProvider: ['type', KiwiAppendComponentContextPadProvider],
};

export default appendComponentModule;
