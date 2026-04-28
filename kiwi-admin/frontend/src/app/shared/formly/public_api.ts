import { ConfigOption } from "@ngx-formly/core";
import { IDictService } from "../dict/dict";
import { FormlyDictExtension } from "./formly-dict.extensions";
import { ColumnEditField, HorizontalFormFieldWrapper, VerticalFormFieldWrapper } from "./formly-field-wrapper";
import { BizTreeSelectType } from "./types/biz-tree-select-type";
import { FormlyFieldCheckbox } from "./types/checkbox-type";
import { AssignmentsEditorType } from "./types/assignments-editor-type";
import { BpmDeclaredOutputType } from "./types/bpm-declared-output-type";
import { ComponentSelectorType } from "./types/component-select-type";
import { ExpressionDialect, ExpressionEditorType } from "./types/expression-editor-type";
import { IconSelectFieldType } from "./types/icon-select.type";

export type FormlyExpressionConfig = {
    /** 统一 expression 类型默认方言，默认 spel */
    defaultDialect?: ExpressionDialect;
};

export function formlyConfig(
    dictService: IDictService,
    expressionConfig: FormlyExpressionConfig = {}
): ConfigOption {
    const defaultDialect: ExpressionDialect = expressionConfig.defaultDialect ?? 'spel';

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
                name: 'bpm-declared-output',
                component: BpmDeclaredOutputType,
                wrappers: ['form-field'],
                defaultOptions: {
                    props: {
                        hideOuterDescription: true,
                    },
                },
            },
            {
                name: 'expression',
                component: ExpressionEditorType,
                wrappers: ['form-field'],
                defaultOptions: {
                    props: {
                        expressionDialect: defaultDialect,
                    },
                },
            },
            {
                // backward compatibility alias
                name: 'spel-expression',
                component: ExpressionEditorType,
                wrappers: ['form-field'],
                defaultOptions: {
                    props: {
                        expressionDialect: 'spel',
                    },
                },
            },
            {
                // backward compatibility alias
                name: 'juel-expression',
                component: ExpressionEditorType,
                wrappers: ['form-field'],
                defaultOptions: {
                    props: {
                        expressionDialect: 'juel',
                    },
                },
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
