import BpmnModeler from 'bpmn-js/lib/Modeler'
import { PropertyDescription } from "./types";
import { ElementModel } from '../extension/element-model';
import { Element } from "bpmn-js/lib/model/Types";
import BaseViewer from 'bpmn-js/lib/BaseViewer';


export class ElementModelProxyHandler {

    constructor(private bpmnModeler: BaseViewer,
        private elementModel: ElementModel,
        private element: Element,
        private properties: PropertyDescription[],
        private viewMode = false,
        private variables: any[] = []
    ) {

    }

    get(target: any, prop: string, receiver: any) {
        let property = this.properties.find(p => p.key == prop);
        if (property) {
            let value = this.elementModel.getValue(this.bpmnModeler, this.element, property.namespace ?? 'bpmn', property.key);
            if (this.viewMode) {
                let rawValue = this.variables.find(v => v.name === prop)?.value;
                if (rawValue !== undefined && rawValue != value) {
                    return `${rawValue} (${value})`;
                }
            }
            return value;
        }
    }


    set(target: any, prop: string, value: any, receiver: any) {
        if (this.viewMode) {
            return true;
        }
        let property = this.properties.find(p => p.key == prop);
        if (property) {
            this.elementModel.setValue(this.bpmnModeler, this.element, property.namespace ?? 'bpmn', property.key, value);
            return true;
        }
        return false;
    }
}