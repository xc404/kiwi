import { AfterViewInit, Component, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { CrudPage, PageConfig } from "@app/shared/components/crud/components/crud-page";
import { AddAction, toolbarAction } from '@app/shared/components/crud/actions';
import { PageHeaderComponent } from "@app/shared/components/page-header/page-header.component";
import { ColumnToken } from '@app/shared/components/table/column';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalModule } from "ng-zorro-antd/modal";
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { BpmInputOutputParameters } from "./bpm-parameters";
import { ModalDragDirective } from '@app/widget/modal/modal-drag.directive';

@Component({
    selector: 'app-bpm',
    templateUrl: './bpm-component.html',
    styles: [`
      .cli-help-hint { margin: 0 0 12px; color: rgba(0,0,0,.65); font-size: 13px; line-height: 1.5; }
    `],
    imports: [PageHeaderComponent, CrudPage, NzModalModule, BpmInputOutputParameters, FormsModule, NzInputModule, ModalDragDirective],
})
export class BpmComponent implements AfterViewInit {

    selectedComponent = signal<any>(null);
    parametersModalVisible = signal(false);

    generateModalVisible = signal(false);
    helpCommandInput = '';

    crudPage = viewChild(CrudPage);
    http = inject(BaseHttpService);
    message = inject(NzMessageService);

    pageConfig: PageConfig;

    constructor() {
        this.pageConfig = {
            title: '组件管理',
            initializeData: true,
            crud: '/bpm/component',
            toolbarActions: [
                AddAction,
                toolbarAction({
                    name: '从命令行生成',
                    icon: 'console-sql',
                    tooltip: '在服务端执行 help 命令并生成组件草稿',
                    handler: () => this.openGenerateFromCli(),
                }),
            ],
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
            ]
        };
    }

    ngAfterViewInit(): void {
    }

    openGenerateFromCli(): void {
        this.helpCommandInput = '';
        this.generateModalVisible.set(true);
    }

    /**
     * 供 nzModal nzOnOk 订阅：成功时关闭弹窗并打开新增表单（预填后端返回的 BpmComponent）。
     */
    confirmGenerateFromCli() {
        const cmd = this.helpCommandInput?.trim();
        if (!cmd) {
            this.message.warning('请输入要执行的 help 命令（如 docker --help）');
            return;
        }
        this.http.post<any>('/bpm/component/from-cli-help', { helpCommand: cmd }).pipe(
            tap((comp) => {
                this.generateModalVisible.set(false);
                this.helpCommandInput = '';
                this.crudPage()?.popupAdd(comp);
                this.message.success('已生成组件草稿，请确认后保存');
            }),
            map(() => true),
            catchError(() => {
                this.message.error('生成失败');
                return of(false);
            })
        ).subscribe();
    }

    saveParameters() {
        this.crudPage()?.reloadTable();
        this.parametersModalVisible.set(false);

    }

}
