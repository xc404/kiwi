import { inject, Injectable } from '@angular/core';

import BpmnModeler from 'bpmn-js/lib/Modeler';
import { Element } from 'bpmn-js/lib/model/Types';

import { PaletteGroup, PaletteItem, PaletteProvider } from './palette-provider';
import { ElementModel } from '../extension/element-model';

@Injectable()
export default class BasePaletteProvider implements PaletteProvider {
  private readonly elementModel = inject(ElementModel);

  getName(): string {
    return '基本元素';
  }
  getPaletteGroup(): PaletteGroup[] {
    return [
      {
        group: '开始事件/结束事件',
        palettes: [
          {
            id: 'StartEvent',
            title: '开始事件',
            icon: 'bpmn-icon-start-event-none',
            options: {}
          },
          {
            id: 'EndEvent',
            title: '结束事件',
            icon: 'bpmn-icon-end-event-none',
            options: {}
          }
        ]
      },
      {
        group: '基本任务',
        palettes: [
          {
            id: 'UserTask',
            title: '用户任务',
            icon: 'bpmn-icon-user-task',
            options: {}
          },
          {
            id: 'ServiceTask',
            title: '服务任务',
            icon: 'bpmn-icon-service-task',
            options: {}
          },
          {
            id: 'ManualTask',
            title: '手工任务',
            icon: 'bpmn-icon-manual-task',
            options: {}
          },
          {
            id: 'ReceiveTask',
            title: '接收任务',
            icon: 'bpmn-icon-receive-task',
            options: {}
          }
        ]
      },
      {
        group: '中间事件',
        palettes: [
          {
            id: 'IntermediateCatchEvent',
            title: '中间消息捕获事件',
            icon: 'bpmn-icon-intermediate-event-catch-message',
            eventDefinitionType: 'bpmn:MessageEventDefinition',
            options: {}
          },
          {
            id: 'IntermediateCatchEvent',
            title: '中间定时捕获事件',
            icon: 'bpmn-icon-intermediate-event-catch-timer',
            eventDefinitionType: 'bpmn:TimerEventDefinition',
            options: {}
          },
          {
            id: 'IntermediateCatchEvent',
            title: '中间信号捕获事件',
            icon: 'bpmn-icon-intermediate-event-catch-signal',
            eventDefinitionType: 'bpmn:SignalEventDefinition',
            options: {}
          }
        ]
      },
      {
        group: '网关',
        palettes: [
          {
            id: 'ExclusiveGateway',
            title: '排他网关',
            icon: 'bpmn-icon-gateway-xor',
            options: {}
          },
          {
            id: 'ParallelGateway',
            title: '并行网关',
            icon: 'bpmn-icon-gateway-parallel',
            options: {}
          }
        ]
      },
      {
        group: '子流程',
        palettes: [
          {
            id: 'CallActivity',
            title: '调用活动',
            icon: 'bpmn-icon-call-activity',
            options: {}
          }
        ]
      }
    ];
  }

  getElementOptions(item: PaletteItem): { type: any; options: any; eventDefinitionType?: string } {
    const t: any = item;
    return {
      type: `bpmn:${t.id}`,
      options: t.options || {},
      eventDefinitionType: t.eventDefinitionType
    };
  }

  initElement(bpmnModeler: BpmnModeler, element: Element, item: PaletteItem): void {
    if (element.type === 'bpmn:CallActivity') {
      this.elementModel.ensurePropagateAllVariables(bpmnModeler, element);
    }
  }
}
