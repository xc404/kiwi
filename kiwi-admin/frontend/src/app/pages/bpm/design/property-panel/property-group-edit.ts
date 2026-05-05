import { Component, computed, inject, input } from "@angular/core";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { FieldEditorConfig, toFormlyConfig } from "@app/shared/components/field/field-editor";
import { FormlyModule } from "@ngx-formly/core";
import { Element } from "bpmn-js/lib/model/Types";
import { ComponentService } from "../../flow-elements/component-service";
import { buildSpelVariableSuggestions } from "../expression/bpm-spel-variable-context";
import { ElementModel } from '../extension/element-model';
import { ElementModelProxyHandler } from './element-model-proxy';
import {
    PropertyDescription,
    toEditFieldConfig,
} from "./types";
import BaseViewer from "bpmn-js/lib/BaseViewer";

@Component({
    selector: 'property-group-edit',
    template: `
    <form   nzLayout="vertical" [formGroup]="form()">
        <formly-form [form]="form()" [model]="model()" [fields]="fields()"></formly-form>
    </form>
    `,
    imports: [FormlyModule, ReactiveFormsModule, FormsModule],
    standalone: true
})
export class PropertyGroupEdit {

    elementModel = inject(ElementModel);
    private readonly componentService = inject(ComponentService);
    properties = input([] as PropertyDescription[]);
    bpmnModeler = input.required<BaseViewer>();
    element = input.required<Element>();
    variables = input<any[]>([]);

    form = computed(() => {
        return new FormGroup({});
    });

    model = computed(() => {
        return new Proxy({}, new ElementModelProxyHandler(this.bpmnModeler(),
            this.elementModel, this.element(),
            this.properties(), false, this.variables()));
    });

    private spelVariableSuggestions = computed(() => {
        try {
            return buildSpelVariableSuggestions(
                this.bpmnModeler(),
                this.elementModel,
                this.componentService,
                this.element()
            );
        } catch {
            return [];
        }
    });

    fields = computed(() => {
        return this.properties().map(p => {
            const config: FieldEditorConfig = toEditFieldConfig(p);
            const baseProps: Record<string, unknown> = { variables: this.variables() };
            if (config.editor === 'expression') {
                baseProps['spelVariables'] = this.spelVariableSuggestions();
                baseProps['expressionDialect'] = this.elementModel.expressionDialect();
            }
            return toFormlyConfig(config, "vertical", baseProps);
        });
    });
}
