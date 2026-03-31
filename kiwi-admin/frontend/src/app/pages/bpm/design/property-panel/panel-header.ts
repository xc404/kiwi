import { Element } from 'bpmn-js/lib/model/Types';
import {
  getLabel
} from 'bpmn-js/lib/features/label-editing/LabelUtil';

import {
  is,
  getBusinessObject
} from 'bpmn-js/lib/util/ModelUtil';

import {
  isExpanded,
  isEventSubProcess,
  isInterrupting
} from 'bpmn-js/lib/util/DiUtil';
import { Component, input } from '@angular/core';
import { NzIconModule } from "ng-zorro-antd/icon";


// helpers ///////////////////////

function isCancelActivity(element: any) {
  const businessObject = getBusinessObject(element);

  return businessObject && businessObject.cancelActivity !== false;
}

function getEventDefinition(element: any) {
  const businessObject = getBusinessObject(element),
    eventDefinitions = businessObject.eventDefinitions;

  return eventDefinitions && eventDefinitions[0];
}

function getRawType(type: string) {
  return type.split(':')[1];
}

function getEventDefinitionPrefix(eventDefinition: any) {
  const rawType = getRawType(eventDefinition.$type);

  return rawType.replace('EventDefinition', '');
}

function isDefaultFlow(element: any) {
  const businessObject = getBusinessObject(element);
  const sourceBusinessObject = getBusinessObject(element.source);

  if (!is(element, 'bpmn:SequenceFlow') || !sourceBusinessObject) {
    return false;
  }

  return sourceBusinessObject.default && sourceBusinessObject.default === businessObject && (
    is(sourceBusinessObject, 'bpmn:Gateway') || is(sourceBusinessObject, 'bpmn:Activity')
  );
}

function isConditionalFlow(element: any) {
  const businessObject = getBusinessObject(element);
  const sourceBusinessObject = getBusinessObject(element.source);

  if (!is(element, 'bpmn:SequenceFlow') || !sourceBusinessObject) {
    return false;
  }

  return businessObject.conditionExpression && is(sourceBusinessObject, 'bpmn:Activity');
}

function isPlane(element: any) {

  // Backwards compatibility for bpmn-js<8
  const di = element && (element.di || getBusinessObject(element).di);

  return is(di, 'bpmndi:BPMNPlane');
}


function getTemplate(element: any, elementTemplates: any) {
  return elementTemplates.get(element);
}

function getTemplateDocumentation(element: any, elementTemplates: any) {
  const template = getTemplate(element, elementTemplates);

  return template && template.documentationRef;
}
{ /* Required to break up imports, see https://github.com/babel/babel/issues/15156 */ }

export function getConcreteType(element: any) {
  const {
    type: elementType
  } = element;

  let type = getRawType(elementType);

  // (1) event definition types
  const eventDefinition = getEventDefinition(element);

  if (eventDefinition) {
    type = `${getEventDefinitionPrefix(eventDefinition)}${type}`;

    // (1.1) interrupting / non interrupting
    if (
      (is(element, 'bpmn:StartEvent') && !isInterrupting(element)) ||
      (is(element, 'bpmn:BoundaryEvent') && !isCancelActivity(element))
    ) {
      type = `${type}NonInterrupting`;
    }

    return type;
  }

  // (2) sub process types
  if (is(element, 'bpmn:SubProcess') && !is(element, 'bpmn:Transaction')) {
    if (isEventSubProcess(element)) {
      type = `Event${type}`;
    } else {
      const expanded = isExpanded(element) && !isPlane(element);
      type = `${expanded ? 'Expanded' : 'Collapsed'}${type}`;
    }
  }

  // (3) conditional + default flows
  if (isDefaultFlow(element)) {
    type = 'DefaultFlow';
  }

  if (isConditionalFlow(element)) {
    type = 'ConditionalFlow';
  }


  return type;
}

@Component(
  {
    selector: 'bpm-panel-header',
    styleUrls: ["./panel-header.scss"],
    template: `
      @if(element()) {
      <div class="bpm-panel-header">
        <h2>{{ getTypeLabel() }}</h2>
        <div class="bpm-panel-header-icons">
         <nz-icon> {{ getElementIcon() }}</nz-icon>
        </div>
      </div>
      }
    `,
    imports: [NzIconModule]
  }
)
export class PanelHeader {

  element = input.required<Element>();

  // getDocumentationRef(element: any) {
  //   const elementTemplates = getTemplatesService();

  //   if (elementTemplates) {
  //     return getTemplateDocumentation(element, elementTemplates);
  //   }
  // }

  getElementLabel() {
    if (is(this.element(), 'bpmn:Process')) {
      return getBusinessObject(this.element()).name;
    }

    return getLabel(this.element());
  }

  getElementIcon() {
    const concreteType = getConcreteType(this.element());

    // eslint-disable-next-line react-hooks/rules-of-hooks
    // const config = useService('config.elementTemplateIconRenderer', false);

    // const { iconProperty = 'zeebe:modelerTemplateIcon' } = config || {};

    // const templateIcon = getBusinessObject(this.element()).get(iconProperty);

    // if (templateIcon) {
    //   return () => <img class="bio-properties-panel-header-template-icon" width = "32" height = "32" src = { templateIcon } alt = "" />;
    // }

    // return iconsByType[concreteType];
  }

  getTypeLabel() {


    const concreteType = getConcreteType(this.element());

    return concreteType;
  }
}



