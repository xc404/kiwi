import { HttpClient } from "@angular/common/http";
import { Component, computed, inject, viewChild } from "@angular/core";
import { BaseHttpService } from "@app/core/services/http/base-http.service";
import { CrudPage, PageConfig } from "@app/shared/components/crud/components/crud-page";
import { PageHeaderComponent } from "@app/shared/components/page-header/page-header.component";
import { ColumnToken } from "@app/shared/components/table/column";
import { Utils } from "@app/utils/utils";
import { environment } from '@env/environment';
import { NzButtonModule } from "ng-zorro-antd/button";
import { NzCardModule } from "ng-zorro-antd/card";
import { NzConfigService } from "ng-zorro-antd/core/config";
import { NzDropDownModule } from "ng-zorro-antd/dropdown";
import { NzIconModule } from "ng-zorro-antd/icon";
import { NzMessageService } from "ng-zorro-antd/message";
import { NzModalService } from "ng-zorro-antd/modal";
import { NzUploadModule } from "ng-zorro-antd/upload";
@Component({
    selector: 'app-codegen',
    templateUrl: 'connection.component.html',
    imports: [
        PageHeaderComponent,
        CrudPage,
        NzCardModule,
        NzButtonModule,
        NzIconModule,
        NzDropDownModule, NzButtonModule,
        NzUploadModule
    ],
    standalone: true
})
export class ConnectionComponent {
    baseApi = "/tools/connection";
    http = inject(BaseHttpService)
    page = viewChild(CrudPage);
    modalService = inject(NzModalService);
    messageService = inject(NzMessageService);
    pageConfig = computed(() => {
        return {
            title: "数据库连接",
            "initializeData": true,
            crud: "/tools/connection",
            columnActions: [
                {
                    name: '测试连接',
                    handler: () => {
                        let record = inject(ColumnToken, { optional: true })?.getRecord();
                        this.testConnection(record);
                    }
                }
            ],
            fields: [
                {
                    name: '名称',
                    dataIndex: 'name',
                    required: true,
                },
                {
                    name: 'jdbcUrl',
                    dataIndex: "jdbcUrl"
                },
                {
                    name: '用户名',
                    dataIndex: "username"
                },
                {
                    name: '密码',
                    dataIndex: "password",
                    column: false
                }
            ]
        } as PageConfig
    })


    testConnection(record: any) {
        let id = record.id;
        this.http.post(this.baseApi + `/${id}/test-connection`, {}).subscribe(res => {
            this.messageService.success('连接成功');
        });


    }

}