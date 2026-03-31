import { Component } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { ComponentSelector } from "@app/pages/bpm/component/component-selector";
import { FieldType, FieldTypeConfig } from '@ngx-formly/core';
import { NzInputModule } from "ng-zorro-antd/input";

@Component({
  selector: 'component-selector-type',
  template: `
    
      <app-component-selector [control]="formControl" ></app-component-selector>
  `,
  imports: [NzInputModule, ComponentSelector],
})
export class ComponentSelectorType extends FieldType<FieldTypeConfig> {

}
