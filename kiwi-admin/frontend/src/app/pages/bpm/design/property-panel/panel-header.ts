import { Component, inject, input } from '@angular/core';

import { getLabel } from 'bpmn-js/lib/features/label-editing/LabelUtil';
import { Element } from 'bpmn-js/lib/model/Types';
import { is, getBusinessObject } from 'bpmn-js/lib/util/ModelUtil';

import { NzIconModule } from 'ng-zorro-antd/icon';

import { ComponentService } from '../../flow-elements/component-service';

@Component({
  selector: 'bpm-panel-header',
  styleUrls: ['./panel-header.scss'],
  template: `
    @if (element()) {
      <div class="bpm-panel-header">
        <div class="bpm-panel-header-titles">
          <h2>{{ getComponentName() || '—' }}</h2>
        </div>
        <div class="bpm-panel-header-icons">
          <nz-icon> {{ getElementIcon() }}</nz-icon>
        </div>
      </div>
    }
  `,
  imports: [NzIconModule]
})
export class PanelHeader {
  private readonly componentService = inject(ComponentService);

  element = input.required<Element>();

  getElementLabel() {
    if (is(this.element(), 'bpmn:Process')) {
      return getBusinessObject(this.element()).name;
    }

    return getLabel(this.element());
  }

  /** 优先组件库名称；否则用元素标签 / Process.name */
  getComponentName(): string {
    const fromCatalog = this.componentService.getComponentForElement(this.element())?.name?.trim();
    if (fromCatalog) {
      return fromCatalog;
    }
    const label = this.getElementLabel();
    return typeof label === 'string' ? label.trim() : '';
  }

  getElementIcon() {
    // const config = useService('config.elementTemplateIconRenderer', false);
    // const { iconProperty = 'zeebe:modelerTemplateIcon' } = config || {};
    // const templateIcon = getBusinessObject(this.element()).get(iconProperty);
    // if (templateIcon) {
    //   return () => <img class="bio-properties-panel-header-template-icon" width = "32" height = "32" src = { templateIcon } alt = "" />;
    // }
    // return iconsByType[concreteType];
  }
}
