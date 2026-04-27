import { ConfigOption } from "@ngx-formly/core";
import { IDictService } from "../dict/dict";
import { FormlyDictExtension } from "./formly-dict.extensions";
import { ColumnEditField, HorizontalFormFieldWrapper, VerticalFormFieldWrapper } from "./formly-field-wrapper";
import { BizTreeSelectType } from "./types/biz-tree-select-type";
import { FormlyFieldCheckbox } from "./types/checkbox-type";
import { AssignmentsEditorType } from "./types/assignments-editor-type";
import { ComponentSelectorType } from "./types/component-select-type";
import { SpelExpressionEditorType } from "./types/spel-expression-editor-type";
import { JuelExpressionEditorType } from "./types/juel-expression-editor-type";
import { IconSelectFieldType } from "./types/icon-select.type";


export function formlyConfig(dictService: IDictService): ConfigOption {

    return {
        wrappers: [
            { name: 'edit-form', component: HorizontalFormFieldWrapper },
            { name: 'column-edit', component: ColumnEditField },
            { name: 'horizontal', component: HorizontalFormFieldWrapper },
            { name: 'vertical', component: VerticalFormFieldWrapper },
        ],
        types: [
            {
                name: 'icon-select',
                component: IconSelectFieldType,
                wrappers: ['form-field'],
            },
            {
                name: 'biz-tree-select',
                component: BizTreeSelectType,
                wrappers: ['form-field'],
            },
            {
                name: 'component-selector',
                component: ComponentSelectorType,
                wrappers: ['form-field'],
            },
            {
                name: 'assignments-editor',
                component: AssignmentsEditorType,
                wrappers: ['form-field'],
            },
            {
                name: 'spel-expression',
                component: SpelExpressionEditorType,
                wrappers: ['form-field'],
            },
            {
                name: 'juel-expression',
                component: JuelExpressionEditorType,
                wrappers: ['form-field'],
            },
            {
                name: 'checkbox',
                component: FormlyFieldCheckbox,
                wrappers: ['form-field'],
            }
        ],
        extensions: [
            {
                name: 'dict',
                extension: new FormlyDictExtension(dictService),
            },
        ]
    };
}
