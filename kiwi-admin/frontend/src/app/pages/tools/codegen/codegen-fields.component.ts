import { Component, computed, effect, inject, input, viewChild } from "@angular/core";
import { AddAction } from "@app/shared/components/crud/actions";
import { CrudPage, PageConfig } from "@app/shared/components/crud/components/crud-page";
import { PageHeaderComponent } from "@app/shared/components/page-header/page-header.component";
import { ColumnToken } from "@app/shared/components/table/column";
import { ModalDragDirective } from "@shared/modal/modal-drag.directive";
import { NzButtonModule } from "ng-zorro-antd/button";
import { NzCardModule } from "ng-zorro-antd/card";
import { NzDropDownModule } from "ng-zorro-antd/dropdown";
import { NzIconModule } from "ng-zorro-antd/icon";
import { NZ_MODAL_DATA, NzModalModule } from "ng-zorro-antd/modal";
import { NzUploadModule } from "ng-zorro-antd/upload";

export interface IModalData {
    table: any
}
@Component({
    selector: 'code-gen-fields',
    templateUrl: 'codegen-fields.component.html',
    imports: [
        CrudPage,
        NzCardModule,
        NzButtonModule,
        NzIconModule,
        NzDropDownModule, NzButtonModule,
        NzModalModule,
        NzUploadModule,
    ],
    standalone: true
})
export class CodegenColumnComponent {
    readonly nzModalData: IModalData = inject(NZ_MODAL_DATA);
    page = viewChild.required(CrudPage);


    entity = input<any>(this.nzModalData);

    constructor() {
        effect(() => {
            let entity: any = this.entity();
            let page = this.page();
            if (entity && page) {
                this.page()!.defaultEditRecord["entityId"] = entity.id;
            }
        });
    }


    pageConfig = {
        title: "表结构",
        crud: "/tools/codegen/field",
        initializeData: true,

        tableConfig: {
            pageSize: 0,
            yScroll: 400,
            type: "edit"
        },
        fields: [
            {
                name: '名称',
                dataIndex: 'columnName',
                fixed: true,
                fixedDir: 'left',
                required: true,
            },
            {
                name: '描述',
                dataIndex: "columnComment"
            },
            {
                name: '数据库类型',
                dataIndex: "columnType",
            },
            {
                name: 'JAVA类型',
                dataIndex: "javaType",
            },
            {
                name: 'JAVA字段名',
                dataIndex: "javaField",
            },
            {
                name: '是否为插入字段',
                dataIndex: "insertable",
            },
            {
                name: '是否编辑字段',
                dataIndex: "updatable",
            },
            {
                name: '是否列表字段',
                dataIndex: "inColumn",
            },
            {
                name: '是否查询字段',
                dataIndex: "inQuery",
            },
            {
                name: '查询方式',
                dataIndex: "queryType",
            },
            {
                name: '前端编辑器',
                dataIndex: "htmlType",
            }

        ],
        search: {   //搜索
            basicParams: { entityId: this.entity().id }
        }
    } as PageConfig

}