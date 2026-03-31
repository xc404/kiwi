import { FieldEditorConfig } from "@app/shared/components/field/field-editor";


enum PropertyNamespace {

    element = "element",
    inputParameter = "inputParameter",
    outputParameter = "outputParameter"
}



export declare type PropertyDescription = {
    key: string;
    name?: string;
    description?: string;
    namespace?: PropertyNamespace;
    htmlType?: string;
    defaultValue?: any;
    readonly?: boolean;
    hidden?: boolean;
    example?: any;
    required?: boolean;
    dictKey?: string;
};


export function toEditFieldConfig(property: PropertyDescription): FieldEditorConfig {
    return {

        ...property,
        dataIndex: property.key,
        name: property.name || property.key,
        editor: property.htmlType || 'text',
    }
}

export { PropertyNamespace };