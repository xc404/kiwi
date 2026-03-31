import { CommonModule } from "@angular/common";
import { Component, computed, input, TemplateRef, Type } from "@angular/core";
import { NzTableSortOrder } from "ng-zorro-antd/table";
import { NzTagModule } from "ng-zorro-antd/tag";
import { AppButton, AppButtonConfig } from "../button/app.button";
import { FieldComp, FieldConfig } from "../field/field";


export abstract class ColumnToken {
    abstract getRecord: () => any;
}

export interface ColumnConfig extends FieldConfig {
    showSort?: boolean; // 是否显示排序
    sortDirection?: NzTableSortOrder; // 排序方向
    width?: number; // 单元格宽度
    thTemplate?: TemplateRef<any> | Type<any>; // th单元格模板
    tdTemplate?: TemplateRef<any> | Type<any>; // td单元格模板
    fixed?: boolean; // 是否固定单元格 （只有从最左边或最右边连续固定才有效）
    fixedDir?: 'left' | 'right'; // 固定在左边还是右边，需要配合fixed来使用
    notNeedEllipsis?: boolean; // 不需要...时给true
    tdClassList?: string[]; // 为td单元格指定类 (父组件中的类必须加上 /deep/ 前缀才能对子组件生效)
    thClassList?: string[]; // 为th单元格指定类  (父组件中的类必须加上 /deep/ 前缀才能对子组件生效)
    show?: boolean; // 是否显示列，false:不显示，其他：显示
    tdClassFn?: (data: any, index: number) => string[];
    thClassFn?: (data: any) => string[];
    mapping?: (data: any) => any;

}
export interface ColumnActionConfig  extends AppButtonConfig {

}

export type ActionsColumnConfig = ColumnConfig & {
    actions: ColumnActionConfig[];
};

export function actionColumn(config: ActionsColumnConfig | any) {
    if (!config.tdTemplate && config.actions) {
        config.tdTemplate = ActionsTableCell;
    }
    return config;
}



@Component({
    template: "",
})
export abstract class ColumnComp extends FieldComp {

    override field = input<ColumnConfig | any>();
}


@Component({
    selector: 'app-table-column-action',
    styles: '',
    template: `
        <app-button [config]="action()"></app-button>
    `,
    imports: [CommonModule,  AppButton],
    standalone: true
})
export class TableColumnAction extends ColumnComp {
    action = input.required<ColumnActionConfig>();
}

@Component({
    selector: 'app-actions-table-cell',
    styles: '',
    template: `
            @for (action of actions(); track $index) { 
                <app-table-column-action [action]="action" [record]="record()" class="m-r-10"></app-table-column-action>
            }
            
    `,
    imports: [CommonModule, TableColumnAction],
    standalone: true
})
export class ActionsTableCell extends ColumnComp {

    actions = computed(() => {
        const config = this.field();
        return config.actions;
    })
}


@Component({
    selector: 'app-table-header-cell',
    styles: '',
    template: `
    @if(!this.field().thTemplate){
        <span>
            {{name()}}
        </span>
    } @else {
         @if (isTemplate()) {
            <ng-container  [ngTemplateOutlet]="this.field().thTemplate"
            [ngTemplateOutletContext]="{value:value(), field:field(),record:record()}"></ng-container>
        } @else {
            <ng-container #instance="ngComponentOutlet"  [ngComponentOutlet]="this.field().thTemplate"
             [ngComponentOutletInputs]="{value:value(), field:field(),record:record()}"></ng-container>
        }
    }`,
    imports: [CommonModule],
    standalone: true
})
export class TableHeaderCell extends ColumnComp {

    isTemplate(): boolean {
        return this.field().thTemplate instanceof TemplateRef;
    }

    isComponent(): boolean {
        return !this.isTemplate() && this.field().thTemplate instanceof Type;
    }
}


@Component({
    selector: 'app-table-cell',
    styles: '',
    template: `
        @if(!this.field().tdTemplate){
            @if(field().dictKey){
                <nz-tag>{{displayValue()}}</nz-tag>
            } @else {
                        <span>
            {{displayValue()}}
        </span>
            }

    } @else {
         @if (isTemplate()) {
           
            <ng-container  [ngTemplateOutlet]="this.field().tdTemplate"
            [ngTemplateOutletContext]="{value:value(), field:field(),record:record()}"></ng-container>
        } @else {
            <ng-container #instance="ngComponentOutlet"  [ngComponentOutlet]="this.field().tdTemplate"
             [ngComponentOutletInputs]="{value:value(), field:field(),record:record()}"></ng-container>
        }
    }`,
    imports: [CommonModule, NzTagModule],
    providers: [{ provide: ColumnToken, useExisting: TableCell }],
    standalone: true
})
export class TableCell extends ColumnComp {

    isTemplate(): boolean {
        return this.field().tdTemplate instanceof TemplateRef;
    }

    isComponent(): boolean {
        return !this.isTemplate() && this.field().tdTemplate instanceof Type;
    }

    getRecord() {
        return this.record();
    }

}
