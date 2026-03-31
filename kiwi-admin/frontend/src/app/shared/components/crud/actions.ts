import { inject } from "@angular/core";
import { AppButtonConfig } from "../button/app.button";
import { ColumnToken } from "../table/column";
import { CrudPageToken } from "./components/crud-page";


export function column() {
    return inject(ColumnToken);
}

export function crudPage() {
    return inject(CrudPageToken);
}

export function columnAction(config: AppButtonConfig):AppButtonConfig {

    return {
        ...config,
        nzSize: config.nzSize || 'small'
    }
}

export function toolbarAction(config: AppButtonConfig) : AppButtonConfig{
    return {
        ...config,
        nzSize: config.nzSize || 'small',
    }
}




export const EditAction = columnAction({
    icon: 'edit',
    tooltip: '编辑',
    nzType: 'primary',
    handler: () => {
        let column = inject(ColumnToken);
        let record = column.getRecord();
        const page = crudPage();
        page.popupEdit(record);
    }
});

export const DeleteAction = columnAction({
    icon: 'delete',
    tooltip: '删除',
    nzDanger: true,
    handler: () => {
        let column = inject(ColumnToken);
        let record = column.getRecord();
        const page = crudPage();
        page.delete(record);
    }
});


export const AddAction = toolbarAction({
    icon: 'plus',
    tooltip: '新增',
    nzType: 'primary',
    handler: () => {
        crudPage().popupAdd();
    }
});

export const DeleteBatchAction = toolbarAction({
    icon: 'delete',
    name: '删除',
    handler: () => {
        const page = crudPage();
        page.deleteItems(page.selectedItems());
    }
});