// import { BpmnModeler } from 'bpmn-js/lib/Modeler';
import BpmnModeler from 'bpmn-js/lib/Modeler'
import { Element } from "bpmn-js/lib/model/Types";
import * as ModelUtil from 'bpmn-js/lib/util/ModelUtil';
import { ElementModel } from "../element-model";
import flowableModdleDescriptor from './flowable.json';
export class FlowableElementModel extends ElementModel {
    override getModdleExtension() {
        return flowableModdleDescriptor;
    }


    public override getValue(bpmnModeler: BpmnModeler, element: Element, namespace: string, key: string): any {
        if (namespace == 'inputParameter') {
            let ele: any = this.getInputParameter(element, key);
            if (ele) {
                return ele.get("value");
            }
            return null;
        }
        if (namespace == 'outputParameter') {
            let ele: any = this.getOutputParameter(element, key);
            if (ele) {
                return ele.get("value");
            }
            return null;
        }

        if (element.type == 'bpmn:ServiceTask') {
            if (key == "componentId") {
                let value = element.businessObject.get("flowable:delegateExpression");
                if (value) {
                    value = value.replace("${", "").replace("}", "");
                }
                return value;
            }
        }
        return super.getValue(bpmnModeler, element, namespace, key);
    }

    public override setValue(bpmnModeler: BpmnModeler, element: Element, namespace: string, key: string, value: any): void {
        if (namespace == 'inputParameter' || namespace == 'outputParameter') {
            let parameter: Element = this.getOrCreateInputOutputParameter(bpmnModeler, element, namespace, key);
            this.updateModdleProperties(bpmnModeler, element, parameter, {
                "value": value
            });
        }
        // if (element.type == 'bpmn:ServiceTask') {
        //     if (key == "componentId") {
        //         let props: any = {}
        //         props["camunda:delegateExpression"] = "${" + value + "}";
        //         this.updateProperties(bpmnModeler, element, props);
        //         return;
        //     }
        // }
        console.log(namespace);

        if (element.type == 'bpmn:ServiceTask') {
            if (namespace == "element") {
                if (key == "componentId") {
                    console.log("set element type", value);
                    var businessObject = ModelUtil.getBusinessObject(element);
                    this.updateModdleProperties(bpmnModeler, element, businessObject, {
                        "camunda:delegateExpression": value
                    });
                }
                return;
            }
        }
        super.setValue(bpmnModeler, element, namespace, key, value);
    }



    getOrCreateInputOutputParameter(bpmnModeler: BpmnModeler, element: Element, namespace: string, key: string): Element {
        const businessObject = ModelUtil.getBusinessObject(element);
        let extensionElements = businessObject.get('extensionElements');

        // (1) ensure extension elements
        if (!extensionElements) {
            extensionElements = this.createElement(bpmnModeler, 'bpmn:ExtensionElements', {
                values: []
            }, businessObject);
            this.updateModdleProperties(bpmnModeler, element, businessObject, {
                extensionElements
            });
        }
        // (2) ensure inputOutput
        let inputOutput = this.getInputOutput(element);
        if (!inputOutput) {
            const parent = extensionElements;
            inputOutput = this.createElement(bpmnModeler, 'camunda:InputOutput', {
                inputParameters: [],
                outputParameters: []
            }, parent);
            this.updateModdleProperties(bpmnModeler, element, extensionElements, {
                values: [...extensionElements.get('values'), inputOutput]
            });
        }
        let parameter: Element | null = null as any;
        if (namespace == 'inputParameter') {
            parameter = this.getInputParameter(element, key);
            if (!parameter) {
                const parent = inputOutput;
                parameter = this.createElement(bpmnModeler, 'camunda:InputParameter', {
                    name: key
                }, parent);
                this.updateModdleProperties(bpmnModeler, element, inputOutput, {
                    inputParameters: [...inputOutput.get('inputParameters'), parameter]
                });
            }
        }
        if (namespace == 'outputParameter') {
            parameter = this.getOutputParameter(element, key);
            if (!parameter) {
                const parent = inputOutput;
                parameter = this.createElement(bpmnModeler, 'camunda:OutputParameter', {
                    name: key
                }, parent);
                this.updateModdleProperties(bpmnModeler, element, inputOutput, {
                    outputParameters: [...inputOutput.get('outputParameters'), parameter]
                });
            }
        }
        return parameter!;
    }

    getInputParameter(root: Element, key: string): Element | null {
        let inputs: any[] = this.getInputParameters(root);

        let e = inputs.filter(i => i.get("name") === key);
        if (e.length > 0) {
            return e[0];
        }
        return null;
    }

    getOutputParameter(root: Element, key: string): Element | null {
        let output: any[] = this.getOutputParameters(root);
        let e = output.filter(i => i.get("name") === key);
        if (e.length > 0) {
            return e[0];
        }
        return null;
    }



    getInputParameters(element: Element): Element[] {
        return this.getParameters(element, 'inputParameters');
    }

    getOutputParameters(element: Element): Element[] {
        return this.getParameters(element, 'outputParameters');
    }


    getParameters(element: Element, prop: string) {
        const inputOutput = this.getInputOutput(element);
        return inputOutput && inputOutput.get(prop) || [];
    }


    getElements(root: Element, type?: string, property?: string) {
        let businessObject = ModelUtil.getBusinessObject(root);
        const elements = this.getExtensionElementsList(businessObject, type);
        return !property ? elements : (elements[0] || {})[property] || [];
    }

    getExtensionElementsList(
        root: Element, type?: string): Element[] {
        let businessObject = ModelUtil.getBusinessObject(root);
        const extensionElements = businessObject.get('extensionElements');
        if (!extensionElements) {
            return [];
        }
        const values = extensionElements.get('values');
        if (!values || !values.length) {
            return [];
        }
        if (type) {
            return values.filter((value: Element) => ModelUtil.is(value, type));
        }
        return values;
    }

    getInputOutput(element: Element) {
        // if (ModelUtil.is(element, 'camunda:Connector')) {
        //     return element.businessObject.get('inputOutput');
        // }
        const businessObject = ModelUtil.getBusinessObject(element);
        return (this.getElements(businessObject, 'camunda:InputOutput') || [])[0];
    }

}