import { inject, Injectable } from "@angular/core";
import { Element } from "bpmn-js/lib/model/Types";
import BpmnModeler from 'bpmn-js/lib/Modeler';
import * as ModelUtil from 'bpmn-js/lib/util/ModelUtil';
import { ElementModel } from '../design/extension/element-model';
import { PaletteItem } from "../design/palette/palette-provider";
import { PropertyDescription } from "../design/property-panel/types";
import { ComponentDescription, ComponentProvider } from './component-provider';


@Injectable({ providedIn: 'root' })
export class ComponentService {



    elementModel: ElementModel = inject(ElementModel);


    componentProvider = inject(ComponentProvider);


    public convertComponentToPalette(c: ComponentDescription): PaletteItem {
        const paletteItem: PaletteItem = { ...c, title: c.name, icon: c.icon || 'bpmn-icon86' };
        return paletteItem;
    }


    public getElementOptions(item: PaletteItem | any): { type: any; options: any; } {
        let t: any = item;
        return {
            type: `bpmn:ServiceTask`, // 这里可以根据组件类型进行映射
            options: {
                name: item.name
            }
        }
    }

    public initElement(bpmnModeler: BpmnModeler, element: Element, item: ComponentDescription | any) {
        // Initialize the business object with the item properties
        // businessObject.name = item.title;
        // businessObject.icon = item.icon;
        // businessObject.componentId = item.key;
        ModelUtil.getBusinessObject(element);
        let type = item.type;
        if (type == "SpringBean") {

        }

        this.setComponentId(bpmnModeler, element, item);
        item.inputParameters?.forEach((p: PropertyDescription) => {
            this.elementModel.setValue(bpmnModeler, element, p.namespace || 'InputParameter', p.key, p.defaultValue || '');
        });

        item.outputParameters?.forEach((p: PropertyDescription) => {
            this.elementModel.setValue(bpmnModeler, element, p.namespace || 'OutputParameter', p.key, p.defaultValue || '');
        });
    }


    setComponentId(bpmnModeler: BpmnModeler, element: Element, component: ComponentDescription) {
        this.elementModel.setValue(bpmnModeler, element, "element", "componentId", component.id);
    }


    getComponentForElement(element: Element): ComponentDescription | undefined {
        const componentId = this.elementModel.getValue(undefined as any, element, "element", "componentId");
        if (!componentId) {
            return undefined;
        }
        return this.componentProvider.getComponent(componentId);
    }

}