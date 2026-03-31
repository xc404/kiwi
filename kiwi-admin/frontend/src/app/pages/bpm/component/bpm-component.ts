import { AfterViewInit, Component, inject, signal, viewChild } from '@angular/core';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { CrudPage, PageConfig } from "@app/shared/components/crud/components/crud-page";
import { PageHeaderComponent } from "@app/shared/components/page-header/page-header.component";
import { ColumnToken } from '@app/shared/components/table/column';
import { NzModalModule } from "ng-zorro-antd/modal";
import { BpmInputOutputParameters } from "./bpm-parameters";

@Component({
    selector: 'app-bpm',
    templateUrl: './bpm-component.html',
    imports: [PageHeaderComponent, CrudPage, NzModalModule, BpmInputOutputParameters],
})
export class BpmComponent implements AfterViewInit {


    selectedComponent = signal<any>(null);
    parametersModalVisible = signal(false);

    crudPage = viewChild(CrudPage);
    pageConfig: PageConfig = {
        title: '组件管理',
        initializeData: true,
        crud: '/bpm/component',
        columnActions: [
            {
                name: '参数设置', handler: () => {
                    let item = inject(ColumnToken).getRecord();
                    this.selectedComponent.set(item);
                    this.parametersModalVisible.set(true);
                }
            }
        ],
        fields: [
            { name: '名称', dataIndex: 'name' },
            { name: '父级ID', dataIndex: 'parentId', editor: 'component-selector' },
            { name: '来源', dataIndex: 'source' },
            { name: '描述', dataIndex: 'description' },
            { name: '分组', dataIndex: 'group' },
            { name: '类型', dataIndex: 'type' },
            // { name: '版本', dataIndex: 'version' },
        ]
    };
    http: any = inject(BaseHttpService);


    ngAfterViewInit(): void {
    }

    saveParameters() {
        // 保存参数设置的逻辑
        // console.log('保存参数:', parameters);
        this.crudPage()?.reloadTable();
        this.parametersModalVisible.set(false);

    }

}