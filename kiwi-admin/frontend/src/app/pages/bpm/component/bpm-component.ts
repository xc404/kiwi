import { AfterViewInit, Component, computed, inject, signal, TemplateRef, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { CrudPage, PageConfig } from "@app/shared/components/crud/components/crud-page";
import { AddAction, toolbarAction } from '@app/shared/components/crud/actions';
import { PageHeaderComponent } from "@app/shared/components/page-header/page-header.component";
import { ColumnToken } from '@app/shared/components/table/column';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzModalModule } from "ng-zorro-antd/modal";
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { BpmInputOutputParameters } from "./bpm-parameters";
import { ModalDragDirective } from '@app/widget/modal/modal-drag.directive';

@Component({
    selector: 'app-bpm',
    templateUrl: './bpm-component.html',
    styles: [`
      .cli-help-hint { margin: 0 0 12px; color: rgba(0,0,0,.65); font-size: 13px; line-height: 1.5; }
    `],
    imports: [
        PageHeaderComponent,
        CrudPage,
        NzModalModule,
        BpmInputOutputParameters,
        FormsModule,
        NzInputModule,
        NzButtonModule,
        NzDropDownModule,
        NzMenuModule,
        NzIconModule,
        NzTooltipModule,
        ModalDragDirective,
    ],
})
export class BpmComponent implements AfterViewInit {

    selectedComponent = signal<any>(null);
    parametersModalVisible = signal(false);

    generateModalVisible = signal(false);
    helpCommandInput = '';
    /** 可选；对应 CliHelpGenerateRequest.helpText，非空时服务端不再执行命令，仅用 helpCommand 推导前缀 */
    helpTextInput = '';

    openApiModalVisible = signal(false);
    /** 对应 OpenApiGenerateRequest.specUrl，与 openApiSpecInput 二选一（同时填时以后端优先拉取 URL） */
    openApiSpecUrlInput = '';
    openApiSpecInput = '';
    /** 可选，对应后端 OpenApiGenerateRequest.baseUrl */
    openApiBaseUrlInput = '';

    crudPage = viewChild(CrudPage);
    /** 工具栏「生成组件」下拉，于 ngAfterViewInit 绑定到 toolbarActions */
    generateDropdownTpl = viewChild<TemplateRef<void>>('generateDropdownTpl');
    http = inject(BaseHttpService);
    message = inject(NzMessageService);

    pageConfig = computed(() => {

        return {
            title: '组件管理',
            initializeData: true,
            crud: '/bpm/component',
            toolbarActions: [AddAction,
                toolbarAction({
                    name: '生成组件',
                    icon: 'down',
                    tooltip: '从命令行或 OpenAPI / Swagger 文档生成',
                    template: this.generateDropdownTpl(),
                })
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


    });

    constructor() {

    }

    ngAfterViewInit(): void {
        const tpl = this.generateDropdownTpl();
        if (!tpl) {
            return;
        }
        // this.pageConfig = {
        //     ...this.pageConfig,
        //     toolbarActions: [
        //         AddAction,
        //         toolbarAction({
        //             name: '生成组件',
        //             icon: 'down',
        //             tooltip: '从命令行或 OpenAPI / Swagger 文档生成',
        //             template: tpl,
        //         }),
        //     ],
        // };
    }

    openGenerateFromCli(): void {
        this.helpCommandInput = '';
        this.helpTextInput = '';
        this.generateModalVisible.set(true);
    }

    openGenerateFromOpenApi(): void {
        this.openApiSpecUrlInput = '';
        this.openApiSpecInput = '';
        this.openApiBaseUrlInput = '';
        this.openApiModalVisible.set(true);
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
        const body: { helpCommand: string; helpText?: string } = { helpCommand: cmd };
        const pasted = this.helpTextInput?.trim();
        if (pasted) {
            body.helpText = pasted;
        }
        this.http.post<any>('/bpm/component/from-cli-help', body).pipe(
            tap((comp) => {
                this.generateModalVisible.set(false);
                this.helpCommandInput = '';
                this.helpTextInput = '';
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

    /**
     * 调用 POST /bpm/component/from-openapi，再逐条 POST /bpm/component 持久化。
     */
    confirmGenerateFromOpenApi(): void {
        const specUrl = this.openApiSpecUrlInput?.trim();
        const spec = this.openApiSpecInput?.trim();
        if (!specUrl && !spec) {
            this.message.warning('请填写文档链接，或粘贴 OpenAPI / Swagger 全文（JSON 或 YAML）');
            return;
        }
        const baseUrl = this.openApiBaseUrlInput?.trim();
        const body: { spec?: string; specUrl?: string; baseUrl?: string } = {
            baseUrl: baseUrl || undefined,
        };
        if (specUrl) {
            body.specUrl = specUrl;
        }
        if (spec) {
            body.spec = spec;
        }
        this.http
            .post<Record<string, unknown>[]>('/bpm/component/from-openapi', body)
            .pipe(
                switchMap((list) => {
                    if (!list?.length) {
                        this.message.warning('未生成任何组件');
                        return of(null);
                    }
                    return forkJoin(
                        list.map((comp) =>
                            this.http.post<unknown>('/bpm/component', comp, { showLoading: false })
                        )
                    );
                }),
                tap((saved) => {
                    if (saved === null) {
                        return;
                    }
                    this.openApiModalVisible.set(false);
                    this.openApiSpecUrlInput = '';
                    this.openApiSpecInput = '';
                    this.openApiBaseUrlInput = '';
                    this.crudPage()?.reloadTable();
                    const n = Array.isArray(saved) ? saved.length : 0;
                    this.message.success(`已根据文档生成并保存 ${n} 个组件`);
                }),
                catchError(() => {
                    this.message.error('生成或保存失败');
                    return of(null);
                })
            )
            .subscribe();
    }

    saveParameters() {
        this.crudPage()?.reloadTable();
        this.parametersModalVisible.set(false);

    }

}
