import { inject, Injectable } from "@angular/core";
import { Element } from "bpmn-js/lib/model/Types";
import BpmnModeler from 'bpmn-js/lib/Modeler';
import * as ModelUtil from 'bpmn-js/lib/util/ModelUtil';
import { ElementModel } from '../design/extension/element-model';
import { PaletteItem } from "../design/palette/palette-provider";
import { PropertyDescription, PropertyNamespace } from "../design/property-panel/types";
import { ComponentDescription, ComponentProvider } from './component-provider';
import { isCallActivity } from "./utils";


@Injectable({ providedIn: 'root' })
export class ComponentService {



    elementModel: ElementModel = inject(ElementModel);


    componentProvider = inject(ComponentProvider);


    public convertComponentToPalette(c: ComponentDescription): PaletteItem {
        if (isCallActivity(c.type)) {
            return {
                ...c,
                bpmnType: 'bpmn:CallActivity',
                title: c.name,
                icon: c.icon || 'bpmn-icon42',
                options: {}
            };
        } else {
            return {
                ...c,
                bpmnType: `bpmn:ServiceTask`,
                title: c.name,
                icon: c.icon || 'bpmn-icon86',
                options: {}
            };
        }
    }


    public getElementOptions(item: PaletteItem | any): { type: any; options: any; } {
        return {

            type: `${item.bpmnType}`,
            options: {
                name: item.name
            }
        }
    }

    public initElement(bpmnModeler: BpmnModeler, element: Element, item: ComponentDescription | any) {
        ModelUtil.getBusinessObject(element);

        let inputNamespace = PropertyNamespace.inputParameter;

        this.setComponentId(bpmnModeler, element, item);
        item.inputParameters?.forEach((p: PropertyDescription) => {
            this.elementModel.setValue(
                bpmnModeler,
                element,
                p.namespace || inputNamespace,
                p.key,
                p.defaultValue ??  ''
            );
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