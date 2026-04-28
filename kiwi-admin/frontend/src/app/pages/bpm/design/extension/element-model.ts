import BaseViewer from "bpmn-js/lib/BaseViewer";
import BpmnFactory from "bpmn-js/lib/features/modeling/BpmnFactory";
import Modeling from "bpmn-js/lib/features/modeling/Modeling";
import { Element } from "bpmn-js/lib/model/Types";
import BpmnModeler from 'bpmn-js/lib/Modeler'
import * as ModelUtil from 'bpmn-js/lib/util/ModelUtil';
export abstract class ElementModel {

    abstract getModdleExtension(): any;

    /**
     * 属性面板里「输入 / In」命名空间且未指定 htmlType 时的默认 Formly 表达式编辑器。
     * Flowable 等为 SpEL；Camunda 引擎表达式为 JUEL。
     */
    expressionEditorFormlyType(): string {
        return 'spel-expression';
    }

    public getValue(bpmnModeler: BaseViewer, element: Element, namespace: string, key: string): any {
        if (namespace == 'bpmn' || namespace == 'element' || !namespace) {

            switch (key) {
                case "name":
                case "id":
                case "componentId":
                    return element.businessObject[key];
                    break;
            }
        }

        if (element.type == "bpmn:sequenceFlow") {
            if (key == "condition") {
                return element.businessObject.conditionExpression?.body;
            }
        }

    }

    public setValue(bpmnModeler: BaseViewer, element: Element, namespace: string, key: string, value: any): void {
        if (namespace == 'bpmn' || namespace == 'element' || !namespace) {
            switch (key) {
                case "name":
                case "id":
                case "componentId":
                    this.updateProperties(bpmnModeler, element, {
                        [key]: value
                    });
                    return;
            }
        }

    }

    public clearComponentProperties(bpmnModeler: BaseViewer, element: Element) {
        if (element.type == "bpmn:ServiceTask") {
            let businessObject = ModelUtil.getBusinessObject(element);
            this.updateModdleProperties(bpmnModeler, element, businessObject, {
                extensionElements: null
            });
        }
    }



    public getChildElement(parent: Element, ...children: string[]): Element {
        let ele = parent;
        for (let child of children) {
            ele = ele.businessObject[child];
            if (ele == null)

                break
        }
        return ele;
    }

    public getOrCreateChildElement(bpmnModeler: BpmnModeler, parent: Element, ...children: string[]): Element {
        let bpmnFactory: BpmnFactory = bpmnModeler.get('bpmnFactory');
        let ele = parent;
        for (let child of children) {
            let childEle = ele.businessObject[child];
            if (childEle == null) {
                childEle = bpmnFactory.create(child);
                ele.businessObject[child] = childEle;
            }
            ele = childEle;
        }
        return ele;
    }

    updateProperties(bpmnModeler: BaseViewer, element: Element, properties: any) {
        let bpmnFactory: BpmnFactory = bpmnModeler.get('bpmnFactory');
        const modeling: Modeling = bpmnModeler.get('modeling');
        modeling.updateProperties(element, properties);
    }



    updateModdleProperties(bpmnModeler: BaseViewer, element: Element, moddleElement: any, properties: any) {
        const modeling: any = bpmnModeler.get('modeling');
        modeling.updateModdleProperties(element, moddleElement, properties);
    }

    createElement(bpmnModeler: BaseViewer, type: string, properties: any, parent?: Element) {
        let bpmnFactory: any = bpmnModeler.get('bpmnFactory');
        const element = bpmnFactory.create(type, properties);
        if (parent) {
            element.$parent = parent;
        }
        return element;
    }

    /** Camunda/Flowable 子类返回 extensionElements 下 inputOutput 的 outputParameters；默认无 */
    getOutputParameters(_element: Element): Element[] {
        return [];
    }

    /** 删除单个 outputParameter（按 name）；默认无实现 */
    removeOutputParameter(_bpmnModeler: BaseViewer, _element: Element, _key: string): void {
    }
}