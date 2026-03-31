import { inject, Injectable } from "@angular/core";
import { ComponentService } from "../../component/component-service";
import { Element } from "bpmn-js/lib/model/Types";
import { PropertyDescription } from "./types";


export declare type PropertyTab = {
    name?: string;
    groups: { name: string, properties: PropertyDescription[], important?: boolean }[]
}

@Injectable({ providedIn: 'root' })
export class PropertyProvider {

    componentService = inject(ComponentService);


    getProperties(element: Element): PropertyTab[] {

        let elementType = element.type;
        let groups: any[] = [];

        groups.push(
            {
                name: "通用", properties: [
                    { key: "id", name: "id", htmlType: "Text", defaultValue: "", readonly: true, example: "", required: true },
                    { key: "name", name: "name", htmlType: "input", defaultValue: "", example: "", required: true }
                ],
                important: true
            }
        )
        if (elementType == "bpmn:ServiceTask") {
            groups.push(
                {
                    name: "组件类型", properties: [
                        { key: "componentId", name: "组件", htmlType: "component-selector", readonly: true, namespace: "element", defaultValue: "", example: "", required: true },
                    ],
                    important: true
                }
            );

            groups.push(
                ...this.componentService.getComponentProperties(element)
            );
        }

        return [
            {
                name: "基础信息",
                groups: groups
            }
        ];
    }


}
