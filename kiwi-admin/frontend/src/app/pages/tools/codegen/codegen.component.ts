import { HttpClient } from "@angular/common/http";
import { Component, computed, inject, OnInit, viewChild } from "@angular/core";
import { CrudPage, PageConfig } from "@app/shared/components/crud/components/crud-page";
import { PageHeaderComponent } from "@app/shared/components/page-header/page-header.component";
import { ColumnToken } from "@app/shared/components/table/column";
import { Utils } from "@app/utils/utils";
import { DictSelector } from "@shared/components/dict-selector/dict-selector";
import { ModalDragDirective } from "@shared/modal/modal-drag.directive";
import { environment } from '@env/environment';
import { NzButtonModule } from "ng-zorro-antd/button";
import { NzCardModule } from "ng-zorro-antd/card";
import { NzConfigService } from "ng-zorro-antd/core/config";
import { NzDropDownModule } from "ng-zorro-antd/dropdown";
import { NzIconModule } from "ng-zorro-antd/icon";
import { NzModalModule, NzModalService } from "ng-zorro-antd/modal";
import { NzUploadModule } from "ng-zorro-antd/upload";
import { CodegenColumnComponent } from "./codegen-fields.component";
import { ImportDatabaseWizardComponent } from "./import-database-wizard";
import { BaseHttpService } from "@app/core/services/http/base-http.service";


@Component({
    selector: 'app-codegen',
    templateUrl: 'codegen.component.html',
    imports: [
        PageHeaderComponent,
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
export class CodegenComponent implements OnInit {

    importBtn = viewChild("importBtn");
    config = inject(NzConfigService)
    page = viewChild(CrudPage);
    baseApi = "/tools/codegen";
    http = inject(HttpClient);
    httpService = inject(BaseHttpService);
    modalService = inject(NzModalService);
    uploadJavaFile(event: any) {

        if (event.file.status === "done") {
            this.page()?.reloadTable();
        }
    }

    uploadJavaUrl() {
        return Utils.joinUrl(environment.api.baseUrl, this.baseApi, "entity/import/javaFile");
    }

    pageConfig = computed(() => {
        let importBtn = this.importBtn();
        return {
            title: "表结构",
            "initializeData": true,
            crud: "/tools/codegen/entity",
            tableConfig: {
                pageSize: 0,
            },
            toolbarActions: [
                {
                    template: importBtn
                }
            ],
            columnActions: [
                {
                    name: '字段设置',
                    handler: () => {
                        let record = inject(ColumnToken, { optional: true })?.getRecord();
                        this.showColumnsModal(record);
                    }
                },
                {
                    name: '预览',
                    handler: () => {
                        let record = inject(ColumnToken, { optional: true })?.getRecord();
                        this.preview(record);
                    }
                }
            ],
            fields: [
                {
                    name: '名称',
                    dataIndex: 'tableName',
                    required: true,
                },
                {
                    name: '描述',
                    dataIndex: "tableComment"
                },
                {
                    name: '实体类名称',
                    dataIndex: "className"
                },
                {
                    name: '前端类型',
                    dataIndex: "webTpl",
                    column: false
                },
                {
                    name: '持久层类型',
                    dataIndex: "daoTpl",
                    column: false
                },
                {
                    name: '生成包路径',
                    dataIndex: "packageName",
                    column: false
                },
                {
                    name: '生成模块名',
                    dataIndex: "moduleName",
                    column: false
                },
                {
                    name: '生成业务名',
                    dataIndex: "businessName",
                    column: false
                },
                {
                    name: '生成功能名',
                    dataIndex: "functionName",
                    column: false
                },
                {
                    name: '生成作者',
                    dataIndex: "functionAuthor",
                    column: false
                }
            ]
        } as PageConfig
    })


    showColumnsModal(record: any) {
        let modalRef = this.modalService.create({
            nzWidth: "75vw",
            nzTitle: '字段设置',
            nzContent: CodegenColumnComponent,
            nzData: record,
            nzFooter: [
                {
                    label: "确定",
                    onClick: (modal: any) => {
                        modalRef.close
                    }
                }
            ]
        });
    }

    showImportModal() {
        this.modalService.create({
            nzWidth: "600px",
            nzTitle: '导入数据库',
            nzContent: ImportDatabaseWizardComponent
        }).afterClose.subscribe(() => {
            this.page()?.reloadTable();
        });
    }

    showImportJsonModal() {
        throw new Error('Method not implemented.');
    }

    preview(record: any) {
        this.httpService.get(Utils.joinUrl(this.baseApi, "entity/" + record.id + "/preview")).subscribe((res: any) => {
            console.log(res);
        });
    }

    ngOnInit(): void {

    }

}