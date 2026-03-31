import  BpmnModeler  from 'bpmn-js/lib/Modeler';
import { Injectable } from "@angular/core";
import { PaletteGroup, PaletteItem, PaletteProvider } from "./palette-provider";
import { Element } from 'bpmn-js/lib/model/Types';

@Injectable()
export default class BasePaletteProvider implements PaletteProvider {

    getName(): string {
        return "基本元素";
    }
    getPaletteGroup(): PaletteGroup[] {
        return [
            {
                group: "开始事件/结束事件",
                palettes: [
                    {
                        id: "StartEvent",
                        title: "开始事件",
                        icon: "bpmn-icon69",
                        options: {}
                    },
                    {
                        id: "EndEvent",
                        title: "结束事件",
                        icon: "bpmn-icon56",
                        options: {}
                    }
                ]
            },
            {
                group: "基本任务",
                palettes: [
                    {
                        id: "UserTask",
                        title: "用户任务",
                        icon: "bpmn-icon24",
                        options: {}
                    },
                    {
                        id: "ServiceTask",
                        title: "服务任务",
                        icon: "bpmn-icon86",
                        options: {}
                    }
                ]
            },
            {
                group: "网关",
                palettes: [
                    {
                        id: "ExclusiveGateway",
                        title: "排他网关",
                        icon: "bpmn-icon53",
                        options: {}
                    },
                    {
                        id: "ParallelGateway",
                        title: "并行网关",
                        icon: "bpmn-icon6",
                        options: {}
                    }
                ]
            },
            {
                group: "子流程",
                palettes: [
                    {
                        id: "CallActivity",
                        title: "调用活动",
                        icon: "bpmn-icon42",
                        options: {}
                    }
                ]
            }
        ]

    }


    getElementOptions(item: PaletteItem): { type: any; options: any; } {
        let t: any = item;
        return {
            type: `bpmn:${t.id}`,
            options: t.options || {}
        }
    }

    initElement(bpmnModeler: BpmnModeler, element: Element, item: PaletteItem): void {

    }

}