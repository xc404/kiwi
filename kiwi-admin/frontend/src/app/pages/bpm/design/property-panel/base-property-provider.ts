import { Injectable } from "@angular/core";
import { Element } from "bpmn-js/lib/model/Types";
import { PropertyDescription, PropertyProvider, PropertyTab } from "./types";

@Injectable({ providedIn: 'root' })
export class BasePropertyProvider implements PropertyProvider {

    getProperties(element: Element): PropertyTab[] {
        const commonProperties: PropertyDescription[] = [
            { key: "id", name: "id", htmlType: "Text", defaultValue: "", readonly: true, example: "", required: true },
            { key: "name", name: "name", htmlType: "input", defaultValue: "", example: "", required: true }
        ];
        if (element.type === 'bpmn:SequenceFlow') {
            commonProperties.push({
                key: "condition",
                name: "condition",
                defaultValue: "",
                example: "",
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
