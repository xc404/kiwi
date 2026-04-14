import { inject, Injectable } from "@angular/core";
import { Element } from "bpmn-js/lib/model/Types";
import { ComponentService } from "./component-service";
import { PropertyDescription, PropertyNamespace, PropertyProvider, PropertyTab } from "../design/property-panel/types";

@Injectable({ providedIn: 'root' })
export class ComponentPropertyProvider implements PropertyProvider {

    private componentService = inject(ComponentService);

    getProperties(element: Element): PropertyTab[] {
        
        if (element.type !== "bpmn:ServiceTask" && element.type !== "bpmn:CallActivity") {
            return [];
        }
        let mainGroups: { name: string; properties: PropertyDescription[]; important?: boolean }[] = [];
        if(element.type === "bpmn:CallActivity") {
            mainGroups = [
                {
                    name: "流程",
                    properties: [
                        { key: "componentId", name: "流程", htmlType: "component-selector", readonly: true, namespace: PropertyNamespace.element, defaultValue: "", example: "", required: true },
                    ],
                }
            ];
        }
      if(element.type === "bpmn:ServiceTask") {
        mainGroups = [
            {
                name: "组件类型",
                properties: [
                    { key: "componentId", name: "组件", htmlType: "component-selector", readonly: true, namespace: PropertyNamespace.element, defaultValue: "", example: "", required: true },
                ],
                important: true
            },
        ];
    }

        const component = this.componentService.getComponentForElement(element);
        if (!component) {
            return [{ name: "基础信息", groups: mainGroups }];
        }

        const inputSplit = this.splitAndGroupByGroup(component.inputParameters || [], "输入");
        const outputSplit = this.splitAndGroupByGroup(component.outputParameters || [], "输出");
        mainGroups.push(...inputSplit.importantGroups, ...outputSplit.importantGroups);

        const tabs: PropertyTab[] = [{ name: "基础信息", groups: mainGroups }];

        const otherGroups: { name: string; properties: PropertyDescription[]; important?: boolean }[] = [];
        if (inputSplit.unimportant.length > 0) {
            otherGroups.push({ name: "输入", properties: inputSplit.unimportant, important: false });
        }
        if (outputSplit.unimportant.length > 0) {
            otherGroups.push({ name: "输出", properties: outputSplit.unimportant, important: false });
        }
        if (otherGroups.length > 0) {
            tabs.push({ name: "其他", groups: otherGroups });
        }

        return tabs;
    }

    /**
     * 按 parameter.group 聚类，组名为 `${group}-${方向后缀}`；important === false 的参数不进入主分组。
     */
    private splitAndGroupByGroup(
        parameters: PropertyDescription[],
        directionSuffix: "输入" | "输出"
    ): {
        importantGroups: { name: string; properties: PropertyDescription[]; important?: boolean }[];
        unimportant: PropertyDescription[];
    } {
        const important: PropertyDescription[] = [];
        const unimportant: PropertyDescription[] = [];
        for (const p of parameters) {
            if (p.important === false) {
                unimportant.push(p);
            } else {
                important.push(p);
            }
        }
        const byGroup = new Map<string, PropertyDescription[]>();
        for (const p of important) {
            const key = (p.group?.trim()) || "默认";
            if (!byGroup.has(key)) {
                byGroup.set(key, []);
            }
            byGroup.get(key)!.push(p);
        }
        const sortedKeys = [...byGroup.keys()].sort((a, b) => a.localeCompare(b, "zh-CN"));
        const importantGroups = sortedKeys.map(key => ({
            name: `${key}-${directionSuffix}`,
            properties: byGroup.get(key)!,
            important: true,
        }));
        return { importantGroups, unimportant };
    }
}
