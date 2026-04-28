// import { BpmnModeler } from 'bpmn-js/lib/Modeler';
import { inject } from '@angular/core';
import { ComponentDescription, ComponentProvider } from '@app/pages/bpm/flow-elements/component-provider';
import BaseViewer from "bpmn-js/lib/BaseViewer";
import BpmnModeler from 'bpmn-js/lib/Modeler';
import { Element } from "bpmn-js/lib/model/Types";
import * as ModelUtil from 'bpmn-js/lib/util/ModelUtil';
import camundaModdleDescriptor from 'camunda-bpmn-moddle/resources/camunda.json';
import { ElementModel } from "../element-model";
import {
    decodeCamundaInputLiteral,
    encodeCamundaInputLiteral,
} from "./camunda-input-literal";

/** 组件赋值为 JSON 字符串，需按字面量持久化，避免 Camunda 将 body 当 JUEL 解析 */
const INPUT_PARAMETER_LITERAL_STRING_KEYS = new Set<string>(['assignments']);
export class CamundaElementModel extends ElementModel {

    override expressionEditorFormlyType(): string {
        return 'juel-expression';
    }

    componentProvider = inject(ComponentProvider);

    override getModdleExtension() {
        return camundaModdleDescriptor;
    }


    public override getValue(bpmnModeler: BpmnModeler, element: Element, namespace: string, key: string): any {
        if (element.type === 'bpmn:SequenceFlow' && key === 'condition') {
            return element.businessObject.conditionExpression?.body ?? null;
        }
        if (element.type === 'bpmn:CallActivity' && namespace === 'In') {
            const binding = this.getCallActivityIns(element).find((i: any) => i.get('target') === key);
            if (binding) {
                const src = binding.get('source');
                if (src != null && src !== '') {
                    return src;
                }
                return binding.get('sourceExpression') ?? null;
            }
            return null;
        }
        if (element.type === 'bpmn:CallActivity' && namespace === 'Out') {
            const binding = this.getCallActivityOuts(element).find((o: any) => o.get('source') === key);
            if (binding) {
                return binding.get('target') ?? null;
            }
            return null;
        }
        if (namespace == 'inputParameter') {
            let ele: any = this.getInputParameter(element, key);
            if (ele) {
                const raw = ele.get("value");
                if (
                    INPUT_PARAMETER_LITERAL_STRING_KEYS.has(key) &&
                    typeof raw === 'string'
                ) {
                    return decodeCamundaInputLiteral(raw);
                }
                return raw;
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

        return super.getValue(bpmnModeler, element, namespace, key);
    }

    public override setValue(bpmnModeler: BpmnModeler, element: Element, namespace: string, key: string, value: any): void {
        if (element.type === 'bpmn:SequenceFlow' && key === 'condition') {
            const str = value == null ? '' : String(value);
            if (!str) {
                this.updateProperties(bpmnModeler, element, {
                    conditionExpression: undefined
                });
                return;
            }
            const conditionExpression = this.createElement(bpmnModeler, 'bpmn:FormalExpression', {
                body: str
            });
            this.updateProperties(bpmnModeler, element, {
                conditionExpression
            });
            return;
        }
        if (element.type === 'bpmn:CallActivity' && namespace === 'In') {
            const inEl = this.getOrCreateCallActivityIn(bpmnModeler, element, key);
            const str = value == null ? '' : String(value);
            this.updateModdleProperties(bpmnModeler, element, inEl, {
                source: str,
                sourceExpression: undefined
            });
            return;
        }
        if (element.type === 'bpmn:CallActivity' && namespace === 'Out') {
            const outEl = this.getOrCreateCallActivityOut(bpmnModeler, element, key);
            const str = value == null ? '' : String(value);
            this.updateModdleProperties(bpmnModeler, element, outEl, {
                target: str
            });
            return;
        }
        if (namespace == 'inputParameter' || namespace == 'outputParameter') {
            let parameter: Element = this.getOrCreateInputOutputParameter(bpmnModeler, element, namespace, key);
            let stored = value;
            if (
                namespace === 'inputParameter' &&
                INPUT_PARAMETER_LITERAL_STRING_KEYS.has(key) &&
                value != null
            ) {
                stored = encodeCamundaInputLiteral(String(value));
            }
            this.updateModdleProperties(bpmnModeler, element, parameter, {
                "value": stored
            });
            return;
        }
        if (namespace == 'property') {
            let parameter: Element = this.getOrCreatePropertyParameter(bpmnModeler, element, key);
            this.updateModdleProperties(bpmnModeler, element, parameter, {
                "value": value
            });
            return;
        }

        if (element.type == 'bpmn:ServiceTask') {
            if (namespace == "element") {
                if (key == "componentId") {
                    this.setComponentId(bpmnModeler, element, value);
                }
                return;
            }
        }
        super.setValue(bpmnModeler, element, namespace, key, value);
    }

    setComponentId(bpmnModeler: BpmnModeler, element: Element, componentId: string) {
        super.setValue(bpmnModeler, element, "element", "componentId", componentId);
        let component: ComponentDescription = this.componentProvider.getComponent(componentId) as any;
        let id = component.id;
        let componentKey = component.key;
        this.setValue(bpmnModeler, element, "property", "componentId", id);
        if (component.type == "SpringBean") {
            this.updateProperties(bpmnModeler, element, {
                "camunda:delegateExpression": "${" + componentKey + "}"
            });
        }

        if (component.type == "SpringExternalTask") {
            this.updateProperties(bpmnModeler, element, {
                "camunda:type": "external",
                "camunda:topic": componentKey
            });
        }




    }




    getOrCreateInputOutputParameter(bpmnModeler: BpmnModeler, element: Element, namespace: string, key: string): Element {

        // (2) ensure inputOutput
        let inputOutput = this.ensureInputOutputElements(bpmnModeler, element);
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



    getOrCreatePropertyParameter(bpmnModeler: BpmnModeler, element: Element, key: string): Element {

        // (2) ensure extensionProperties
        let extensionProperties: any = this.ensureExtensionPropertiesElements(bpmnModeler, element);
        const values: any[] = extensionProperties.get('values') || [];
        const matching = values.filter((p: any) => p.get('name') === key);
        let parameter = matching[0] as Element | undefined;
        if (matching.length > 1) {
            const deduped = values.filter((p: any) => p.get('name') !== key || p === matching[0]);
            this.updateModdleProperties(bpmnModeler, element, extensionProperties, {
                values: deduped
            });
        }
        if (!parameter) {
            const parent = extensionProperties;
            parameter = this.createElement(bpmnModeler, 'camunda:Property', {
                name: key
            }, parent);
            this.updateModdleProperties(bpmnModeler, element, parent, {
                values: [...extensionProperties.get('values'), parameter]
            });
        }
        return parameter!;
    }

    private ensureExtensionElements(bpmnModeler: BpmnModeler<null>, root: Element) {

        const businessObject = ModelUtil.getBusinessObject(root);
        let extensionElements = businessObject.get('extensionElements');
        if (!extensionElements) {
            extensionElements = this.createElement(bpmnModeler, 'bpmn:ExtensionElements', {
                values: []
            }, businessObject);
            this.updateModdleProperties(bpmnModeler, root, businessObject, {
                extensionElements
            });
        }
        return extensionElements;
    }

    private ensureInputOutputElements(bpmnModeler: BpmnModeler<null>, root: Element) {
        let extensionElements = this.ensureExtensionElements(bpmnModeler, root);
        let inputOutput = this.getInputOutput(root);
        if (!inputOutput) {
            const parent = extensionElements;
            inputOutput = this.createElement(bpmnModeler, 'camunda:InputOutput', {
                inputParameters: [],
                outputParameters: []
            }, parent);
            this.updateModdleProperties(bpmnModeler, root, extensionElements, {
                values: [...extensionElements.get('values'), inputOutput]
            });
        }
        return inputOutput;
    }

    private getInputParameter(root: Element, key: string): Element | null {
        let inputs: any[] = this.getInputParameters(root);

        let e = inputs.filter(i => i.get("name") === key);
        if (e.length > 0) {
            return e[0];
        }
        return null;
    }

    private getOutputParameter(root: Element, key: string): Element | null {
        let output: any[] = this.getOutputParameters(root);
        let e = output.filter(i => i.get("name") === key);
        if (e.length > 0) {
            return e[0];
        }
        return null;
    }



    private getCallActivityIns(element: Element): any[] {
        return this.getExtensionElementsList(element, 'camunda:In');
    }

    private getCallActivityOuts(element: Element): any[] {
        return this.getExtensionElementsList(element, 'camunda:Out');
    }

    private getOrCreateCallActivityIn(bpmnModeler: BpmnModeler, element: Element, targetVar: string): Element {
        const existing = this.getCallActivityIns(element).find((i: any) => i.get('target') === targetVar);
        if (existing) {
            return existing;
        }
        const extensionElements = this.ensureExtensionElements(bpmnModeler, element);
        const inEl = this.createElement(bpmnModeler, 'camunda:In', {
            target: targetVar,
            source: ''
        }, extensionElements);
        this.updateModdleProperties(bpmnModeler, element, extensionElements, {
            values: [...extensionElements.get('values'), inEl]
        });
        return inEl;
    }

    private getOrCreateCallActivityOut(bpmnModeler: BpmnModeler, element: Element, sourceVar: string): Element {
        const existing = this.getCallActivityOuts(element).find((o: any) => o.get('source') === sourceVar);
        if (existing) {
            return existing;
        }
        const extensionElements = this.ensureExtensionElements(bpmnModeler, element);
        const outEl = this.createElement(bpmnModeler, 'camunda:Out', {
            source: sourceVar,
            target: ''
        }, extensionElements);
        this.updateModdleProperties(bpmnModeler, element, extensionElements, {
            values: [...extensionElements.get('values'), outEl]
        });
        return outEl;
    }

    private getInputOutput(root: Element) {
        // if (ModelUtil.is(element, 'camunda:Connector')) {
        //     return element.businessObject.get('inputOutput');
        // }
        const businessObject = ModelUtil.getBusinessObject(root);
        return (this.getElements(businessObject, 'camunda:InputOutput') || [])[0];
    }


    getInputParameters(element: Element): Element[] {
        const inputOutput = this.getInputOutput(element);
        return inputOutput && inputOutput.get("inputParameters") || [];
    }

    override getOutputParameters(element: Element): Element[] {
        // return this.getParameters(element, 'outputParameters');
        const inputOutput = this.getInputOutput(element);
        return inputOutput && inputOutput.get("outputParameters") || [];
    }

    override removeOutputParameter(bpmnModeler: BaseViewer, element: Element, key: string): void {
        const inputOutput = this.ensureInputOutputElements(bpmnModeler as BpmnModeler, element);
        const existing: any[] = [...(inputOutput.get("outputParameters") || [])];
        const next = existing.filter((p: any) => String(p.get("name")) !== key);
        if (next.length === existing.length) {
            return;
        }
        this.updateModdleProperties(bpmnModeler, element, inputOutput, {
            outputParameters: next
        });
    }


    private ensureExtensionPropertiesElements(bpmnModeler: BpmnModeler<null>, root: Element) {


        let extensionElements = this.ensureExtensionElements(bpmnModeler, root);
        let inputOutput = this.getExtensionProperties(root);
        if (!inputOutput) {
            const parent = extensionElements;
            inputOutput = this.createElement(bpmnModeler, 'camunda:Properties', {
                values: []
            }, parent);
            this.updateModdleProperties(bpmnModeler, root, extensionElements, {
                values: [...extensionElements.get('values'), inputOutput]
            });
        }
        return inputOutput;

    }

    getExtensionProperties(root: Element): Element {
        const businessObject = ModelUtil.getBusinessObject(root);
        return (this.getElements(businessObject, 'camunda:Properties') || [])[0];
    }


    getExtensionProperty(root: Element, key: string): Element | null {
        let propertyEles: any = this.getExtensionProperties(root);

        // camunda:Properties stores child camunda:property elements in `values`, not `property`
        let properties: any[] = propertyEles && propertyEles.get("values") || [];


        let e = properties.filter(i => i.get("name") === key);
        if (e.length > 0) {
            return e[0];
        }
        return null;
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

}