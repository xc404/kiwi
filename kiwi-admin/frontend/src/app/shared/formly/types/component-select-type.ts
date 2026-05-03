import { Component } from "@angular/core";
import { ComponentSelector } from "@app/pages/bpm/flow-elements/component-selector";
import { FieldType, FieldTypeConfig } from '@ngx-formly/core';
import { NzInputModule } from "ng-zorro-antd/input";

@Component({
  selector: 'component-selector-type',
  template: `
    
      <bpm-component-selector [control]="formControl" ></bpm-component-selector>
  `,
  imports: [NzInputModule, ComponentSelector],
})
export class ComponentSelectorType extends FieldType<FieldTypeConfig> {

}
