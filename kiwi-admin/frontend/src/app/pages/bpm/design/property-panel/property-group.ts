import { Component, computed, inject, input } from "@angular/core";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { toFormlyConfig } from "@app/shared/components/field/field-editor";
import { FormlyModule } from "@ngx-formly/core";
import { Element } from "bpmn-js/lib/model/Types";
import BpmnModeler from 'bpmn-js/lib/Modeler';
import { ElementModel } from '../extension/element-model';
import { ElementModelProxyHandler } from './element-model-proxy';
import { PropertyDescription, toEditFieldConfig } from "./types";
@Component({
    selector: 'property-group',
    template: `
    <form   nzLayout="vertical" [formGroup]="form()">
        <formly-form [form]="form()" [model]="model()" [fields]="fields()"></formly-form>
    </form>
    `,
    imports: [FormlyModule, ReactiveFormsModule, FormsModule],
    standalone: true
})
export class PropertyGroup {

    elementModel = inject(ElementModel);
    properties = input([] as PropertyDescription[]);
    bpmnModeler = input.required<BpmnModeler>();
    element = input.required<Element>();

    form = computed(() => {
        return new FormGroup({});
    });

    model = computed(() => {
        return new Proxy({}, new ElementModelProxyHandler(this.bpmnModeler(), this.elementModel, this.element(), this.properties()));
    });
    fields = computed(() => {
        return this.properties().map(p => {

            let config = toEditFieldConfig(p);

            return toFormlyConfig(config, "horizontal");
        });
    });
}