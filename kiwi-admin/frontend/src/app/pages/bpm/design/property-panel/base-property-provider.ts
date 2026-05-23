import { Injectable } from "@angular/core";
import { Element } from "bpmn-js/lib/model/Types";
import { PropertyDescription, PropertyProvider, PropertyTab } from "./types";

@Injectable({ providedIn: 'root' })
export class BasePropertyProvider implements PropertyProvider {

    getProperties(element: Element): PropertyTab[] {
        const idEditable = element.type === 'bpmn:ManualTask';
        const commonProperties: PropertyDescription[] = [
            { key: "id", name: "ID", htmlType: idEditable ? "input" : "Text", defaultValue: "", readonly: !idEditable, example: "", required: true },
            { key: "name", name: "名称", htmlType: "input", defaultValue: "", example: "" }
        ];
        if (element.type === 'bpmn:SequenceFlow') {
            commonProperties.push({
                key: "condition",
                name: "条件",
                htmlType: "expression",
                defaultValue: "",
            });
        }
        const groups: { name: string; properties: PropertyDescription[]; important?: boolean }[] = [
            {
                name: "通用",
                properties: commonProperties,
                important: true
            }
        ];
        return [
            {
                name: "基础信息",
                groups
            }
        ];
    }
}
