import { Component, computed, input, output, TemplateRef, Type } from "@angular/core";
import { FieldComp, FieldConfig, FieldType } from "./field";
import { FormlyFieldConfig } from "@ngx-formly/core";


export enum Editor {
    Int = "Int",
    Double = "Double",
    Date = "Date",
    DateTime = "DateTime",
    Text = "Text",
    TextArea = "TextArea",
    Enum = "Enum",
    Radio = "Radio",
    CheckBox = "CheckBox",
    CheckboxGroup = "CheckboxGroup",
    ComboBox = "ComboBox",
    MultiCheckBox = "MultiCheckBox",
    EditComboBox = "EditComboBox",
    ImageSelector = "ImageSelector",
    Template = "Template",
}

export interface FieldEditorConfig extends FieldConfig {
    editor?: Editor | Type<any> | TemplateRef<any> | string;
    /** 统一 expression 编辑器的表达式方言 */
    validator?: any;
    defaultValue?: any;
    hidden?: boolean;
    width?: any;
    required?: boolean;
    readonly?: boolean;
    /** 静态下拉选项（如 ComboBox/select），与 dictKey 二选一；存在时优先于字典 */
    options?: Array<{ label: string; value: unknown }>;
}

function unifyConfig(config: FieldEditorConfig) {
    config = { ...config };
    config.type = config.type || FieldType.String;
    if (!config.editor) {
        if (config.dictKey && config.type != FieldType.Boolean && config.type != FieldType.Date && config.type != FieldType.DateTime) {
            config.editor = Editor.ComboBox;
            return config;
        }
        switch (config.type) {
            case FieldType.Int:
                config.editor = Editor.Int;
                break;
            case FieldType.Double:
                config.editor = Editor.Double;
                break;
            case FieldType.Date:
                config.editor = Editor.Date;
                break;
            case FieldType.DateTime:
                config.editor = Editor.DateTime;
                break;
            case FieldType.Boolean:
                config.editor = Editor.CheckBox;
                break;
            case FieldType.Enum:
                config.editor = Editor.Enum;
                break;
            default:
                config.editor = Editor.Text;
        }
    }
    return config;
}

export function toFormlyConfig(config: FieldEditorConfig, wrapper = 'form-field', props?: any): FormlyFieldConfig {
    var hidden = config.hidden || props?.hidden;
    config = unifyConfig(config);
    // console.log(config.className);
    var className = props?.className || '';
    if (hidden) {
        className += ' hidden';
    }

    return {
        key: config.dataIndex,
        name: config.name,
        wrappers: [wrapper],
        defaultValue: config.defaultValue,
        className,
        type: toFormlyType(config.editor),

        props: {
            ...props,
            description: config.description,
            label: config.name,
            dictKey: config.dictKey,
            type: getInputType(config.editor),
            // indeterminate: false,
            required: config.required,
            readonly: props?.readonly || config.readonly,
            disabled: props?.readonly || config.readonly || props?.disabled,
            hidden: hidden,
            expressionDialect: config.expressionDialect,
            ...(config.options?.length
                ? { options: config.options, valueProp: 'value', labelProp: 'label' }
                : {}),

        },
    }
}

export function toFormlyType(editor: Editor | any): string | Type<any> {
    switch (editor) {
        case Editor.Text: return "input";
        case Editor.Int: return "input";
        case Editor.Double: return "input";
        case Editor.TextArea: return "textarea";
        case Editor.Date: return "datepicker";
        case Editor.DateTime: return "datepicker";
        case Editor.CheckboxGroup: return "checkbox";
        case Editor.Radio: return "radio";
        case Editor.CheckBox: return "checkbox";
        case Editor.ComboBox: return "select";
        default:
            return editor as any;
    }
}

export function getInputType(editor: Editor | any): string | undefined {
    switch (editor) {
        case Editor.Text: return "text";
        case Editor.Int: return "number";
        case Editor.Double: return "number";
    }
    return;
}

@Component({
    template: ""
})
export class FieldEditor extends FieldComp {

    valueChange = output<any>();

    override field = input<FieldEditorConfig | any>();
    setValue(value: any) {
        if (this.record()) {
            this.record()[this.dataIndex()] = value;
        } else {
            this.value.set(value)
        }

        this.valueChange.emit(value);
    }

    editor = computed(() => {
        return this.field().editor;
    })

}

