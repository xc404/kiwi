import { Injectable } from '@angular/core';

import { Element } from 'bpmn-js/lib/model/Types';

import { PropertyDescription, PropertyProvider, PropertyTab } from './types';

@Injectable({ providedIn: 'root' })
export class BasePropertyProvider implements PropertyProvider {
  getProperties(element: Element): PropertyTab[] {
    const idEditable = element.type === 'bpmn:ManualTask';
    const commonProperties: PropertyDescription[] = [
      { key: 'id', name: 'ID', htmlType: idEditable ? 'input' : 'Text', defaultValue: '', readonly: !idEditable, example: '', required: true },
      { key: 'name', name: '名称', htmlType: 'input', defaultValue: '', example: '' }
    ];
    if (element.type === 'bpmn:SequenceFlow') {
      commonProperties.push({
        key: 'condition',
        name: '条件',
        htmlType: 'expression',
        defaultValue: ''
      });
    }
    const groups: Array<{ name: string; properties: PropertyDescription[]; important?: boolean }> = [
      {
        name: '通用',
        properties: commonProperties,
        important: true
      }
    ];

    const eventProperties = buildEventProperties(element);
    if (eventProperties.length > 0) {
      groups.push({
        name: '事件配置',
        properties: eventProperties,
        important: true
      });
    }

    return [
      {
        name: '基础信息',
        groups
      }
    ];
  }
}

/**
 * 根据节点类型和 eventDefinition 类型，返回事件专属属性字段。
 * <ul>
 *   <li>MessageEventDefinition / ReceiveTask → messageName（input）</li>
 *   <li>SignalEventDefinition → signalName（input）</li>
 *   <li>TimerEventDefinition → timerType（ComboBox）+ timerValue（input）</li>
 * </ul>
 * 字段对应的 getValue/setValue 在 {@link CamundaElementModel}（或父类 ElementModel）里实现。
 */
function buildEventProperties(element: Element): PropertyDescription[] {
  if ((element as { type?: string }).type === 'bpmn:ReceiveTask') {
    return [messageNameField()];
  }
  const bo: any = (element as any).businessObject;
  const definitions: any[] = bo?.eventDefinitions ?? [];
  if (!definitions.length) {
    return [];
  }
  const firstType = definitions[0]?.$type;
  switch (firstType) {
    case 'bpmn:MessageEventDefinition':
      return [messageNameField()];
    case 'bpmn:SignalEventDefinition':
      return [signalNameField()];
    case 'bpmn:TimerEventDefinition':
      return [timerTypeField(), timerValueField()];
    default:
      return [];
  }
}

function messageNameField(): PropertyDescription {
  return {
    key: 'messageName',
    name: '消息名称',
    htmlType: 'input',
    defaultValue: '',
    required: true,
    description: '运行时按此名 correlate；同名 <bpmn:Message> 会自动复用，找不到则在 definitions.rootElements 下新建'
  };
}

function signalNameField(): PropertyDescription {
  return {
    key: 'signalName',
    name: '信号名称',
    htmlType: 'input',
    defaultValue: '',
    required: true,
    description: '运行时按此名广播；同名 <bpmn:Signal> 会自动复用，找不到则新建'
  };
}

function timerTypeField(): PropertyDescription {
  return {
    key: 'timerType',
    name: 'Timer 类型',
    htmlType: 'ComboBox',
    defaultValue: 'timeDuration',
    required: true,
    options: [
      { label: 'duration（ISO 8601 持续时间，如 PT30S）', value: 'timeDuration' },
      { label: 'date（绝对时刻，如 2026-06-01T00:00:00）', value: 'timeDate' },
      { label: 'cycle（周期/cron，如 R3/PT1H 或 0 0/5 * * * ?）', value: 'timeCycle' }
    ]
  };
}

function timerValueField(): PropertyDescription {
  return {
    key: 'timerValue',
    name: 'Timer 值',
    htmlType: 'input',
    defaultValue: '',
    required: true,
    description: '示例：PT30S / 2026-06-01T00:00:00 / R3/PT1H'
  };
}
