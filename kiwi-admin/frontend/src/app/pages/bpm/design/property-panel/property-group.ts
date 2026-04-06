import { Component, computed, inject, input } from "@angular/core";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { FieldEditorConfig, toFormlyConfig } from "@app/shared/components/field/field-editor";
import { FormlyModule } from "@ngx-formly/core";
import { Element } from "bpmn-js/lib/model/Types";
import { ComponentService } from "../../component/component-service";
import { buildSpelVariableSuggestions } from "../expression/bpm-spel-variable-context";
import { ElementModel } from '../extension/element-model';
import { ElementModelProxyHandler } from './element-model-proxy';
import { PropertyDescription, toEditFieldConfig } from "./types";
import BaseViewer from "bpmn-js/lib/BaseViewer";
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
    private readonly componentService = inject(ComponentService);
    properties = input([] as PropertyDescription[]);
    bpmnModeler = input.required<BaseViewer>();
    element = input.required<Element>();
    viewMode = input(false);
    variables = input<any[]>([]);

    form = computed(() => {
        return new FormGroup({});
    });

    model = computed(() => {
        return new Proxy({}, new ElementModelProxyHandler(this.bpmnModeler(), 
        this.elementModel, this.element(),
         this.properties(), this.viewMode(), this.variables()));
    });

    /** SpEL 编辑器：`$` 补全用的变量（图中引用 + 上游输出） */
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
            let config: FieldEditorConfig;
            if (this.viewMode()) {
                config = this.toViewFieldConfig(p);
            } else {

                config = this.toEditFieldConfig(p);
            }

            const baseProps: Record<string, unknown> = { variables: this.variables() };
            if (p.htmlType === 'spel-expression') {
                baseProps['spelVariables'] = this.spelVariableSuggestions();
            }
            return toFormlyConfig(config, "horizontal", baseProps);
        });
    });

    toEditFieldConfig(property: PropertyDescription): FieldEditorConfig {
        return {

            ...property,
            dataIndex: property.key,
            name: property.name || property.key,
            editor: property.htmlType || '#text',
        } as FieldEditorConfig;
    }

    toViewFieldConfig(property: PropertyDescription): FieldEditorConfig {
        let editor = '#text';
        if (property.htmlType) {
            editor = property.htmlType;
        }
        return {
            ...property,
            dataIndex: property.key,
            name: property.name || property.key,
            readonly: true,
            editor: editor,
        } as FieldEditorConfig;
    }
}