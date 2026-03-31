import { Injectable } from "@angular/core";
import { Element } from "bpmn-js/lib/model/Types";
import { PropertyDescription, PropertyProvider, PropertyTab } from "./types";

@Injectable({ providedIn: 'root' })
export class BasePropertyProvider implements PropertyProvider {

    getProperties(element: Element): PropertyTab[] {
        const groups: { name: string; properties: PropertyDescription[]; important?: boolean }[] = [
            {
                name: "通用",
                properties: [
                    { key: "id", name: "id", htmlType: "Text", defaultValue: "", readonly: true, example: "", required: true },
                    { key: "name", name: "name", htmlType: "input", defaultValue: "", example: "", required: true }
                ],
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
