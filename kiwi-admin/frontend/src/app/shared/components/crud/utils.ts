import { FormlyFieldConfig } from "@ngx-formly/core";
import { Editor, FieldEditorConfig, toFormlyConfig } from "../field/field-editor";
import { ColumnActionConfig, ColumnConfig } from "../table/column";

export const IDColumn = "id";

export type CrudFieldConfig = (ColumnConfig & FieldEditorConfig & {
    column?: 'visiable' | 'disabled' | 'invisable' | false,
    search?: boolean | {
        editor?: Editor
    },
    edit?: false | {
        create?: 'readonly' | 'enabled' | 'hidden' | 'disabled' | false,
        update?: 'readonly' | 'enabled' | 'hidden' | 'disabled' | false,
        editor?: Editor
    },
    actions?: ColumnActionConfig[];
});

export function toFormField(field: CrudFieldConfig, mode: 'search' | 'create' | 'update'): FormlyFieldConfig | null {
    if (mode === 'search') {
        if (field.search === false) {
            return null;
        }
        let editor = (field.search === true ? undefined : field.search?.editor) || field.editor;
        return toFormlyConfig({ ...field, editor: editor, required: false, defaultValue: undefined });
    }
    let edit: any = field.edit;
    if (edit === false) {
        return null;
    }
    edit = edit || {};
    let readonly = false, disabled = false, hidden = false;
    let prop = mode == 'create' ? edit.create : edit.update;
    if (prop === false) {
        return null;
    }
    let editor = edit.editor || field.editor;
    if (prop === false) {
        return null;
    }
    if (prop === 'readonly') {
        readonly = true;
    }
    if (prop === 'disabled') {
        disabled = true;
    }
    if (prop === 'hidden') {
        hidden = true
    }
    if (field.dataIndex == IDColumn && field.edit === undefined) {
        hidden = true;
    }
    if (disabled) {
        return null;
    }
    let props = { readonly, disabled, hidden };
    return toFormlyConfig({ ...field, editor }, 'edit-form', props);

}



export function getActionColumnWidth(columnActions: ColumnActionConfig[]) {
    return columnActions.filter(a => a.name).map(a => a.name).join().length * 16
        + 22 * columnActions.filter(a => a.icon).length
        + (columnActions.length + 1) * 20;
}