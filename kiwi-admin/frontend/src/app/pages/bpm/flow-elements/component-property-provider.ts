import { inject, Injectable } from "@angular/core";
import { Element } from "bpmn-js/lib/model/Types";
import { ComponentService } from "./component-service";
import {
    PropertyDescription,
    PropertyNamespace,
    PropertyProvider,
    PropertyTab,
} from "../design/property-panel/types";

@Injectable({ providedIn: 'root' })
export class ComponentPropertyProvider implements PropertyProvider {

    private componentService = inject(ComponentService);

    getProperties(element: Element): PropertyTab[] {

        if (element.type !== "bpmn:ServiceTask" && element.type !== "bpmn:CallActivity") {
            return [];
        }

        const bindingGroups = this.buildBindingGroups(element);
        const component = this.componentService.getComponentForElement(element);

        const inputSplit = component
            ? this.splitAndGroupByGroup(component.inputParameters || [], "输入")
            : { importantGroups: [] as { name: string; properties: PropertyDescription[]; important?: boolean }[], unimportant: [] as PropertyDescription[] };

        const inputTabGroups: { name: string; properties: PropertyDescription[]; important?: boolean }[] = [
            ...bindingGroups,
            ...inputSplit.importantGroups,
        ];
        if (inputSplit.unimportant.length > 0) {
            inputTabGroups.push({ name: "其他", properties: inputSplit.unimportant, important: false });
        }

        const markedOutputs: PropertyDescription[] = (component?.outputParameters ?? []).map((p) => ({
            ...p,
            declaredOutputParameter: true,
        }));

        const outputSplit = component
            ? this.splitAndGroupByGroup(markedOutputs, "输出")
            : { importantGroups: [] as { name: string; properties: PropertyDescription[]; important?: boolean }[], unimportant: [] as PropertyDescription[] };

        const outputTabGroups: { name: string; properties: PropertyDescription[]; important?: boolean }[] = [
            ...outputSplit.importantGroups,
        ];
        if (outputSplit.unimportant.length > 0) {
            outputTabGroups.push({ name: "其他", properties: outputSplit.unimportant, important: false });
        }

        return [
            { name: "输入", groups: inputTabGroups },
            { name: "输出", groups: outputTabGroups },
        ];
    }

    private buildBindingGroups(element: Element): { name: string; properties: PropertyDescription[]; important?: boolean }[] {
        if (element.type === "bpmn:CallActivity") {
            return [
                {
                    name: "流程",
                    properties: [
                        {
                            key: "componentId",
                            name: "流程",
                            htmlType: "component-selector",
                            readonly: true,
                            namespace: PropertyNamespace.element,
                            defaultValue: "",
                            example: "",
                            required: true,
                        },
                    ],
                    important: true,
                },
            ];
        }
        return [
            {
                name: "组件类型",
                properties: [
                    {
                        key: "componentId",
                        name: "组件",
                        htmlType: "component-selector",
                        readonly: true,
                        namespace: PropertyNamespace.element,
                        defaultValue: "",
                        example: "",
                        required: true,
                    },
                ],
                important: true,
            },
        ];
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
