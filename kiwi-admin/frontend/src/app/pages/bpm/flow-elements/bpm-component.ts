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
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { BpmInputOutputParameters } from "./bpm-parameters";
import { ModalDragDirective } from '@shared/modal/modal-drag.directive';
import { ArrayResult } from '@app/core/services/types';

/** 与后端 BpmComponentPreviewConflictItem DTO 对齐 */
export interface BpmPreviewConflictItem {
    index: number;
    conflict: boolean;
    existingId?: string | null;
    existingName?: string | null;
    sourceKey?: string | null;
    duplicateOfBatchIndex?: number | null;
}

type ConflictSaveAction = 'cancel' | 'overwrite' | 'add';

@Component({
    selector: 'app-bpm',
    templateUrl: './bpm-component.html',
    styles: [`
      .cli-help-hint { margin: 0 0 12px; color: rgba(0,0,0,.65); font-size: 13px; line-height: 1.5; }
      .conflict-row { margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0; }
      .conflict-row:last-child { border-bottom: none; }
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
        NzRadioModule,
        ModalDragDirective,
    ],
})
export class BpmComponent implements AfterViewInit {

    selectedComponent = signal<any>(null);
    parametersModalVisible = signal(false);

    /** 生成结果保存：sourceKey 冲突确认 */
    conflictModalVisible = signal(false);
    pendingDrafts = signal<any[]>([]);
    previewConflictItems = signal<BpmPreviewConflictItem[]>([]);
    conflictActions = signal<ConflictSaveAction[]>([]);
    private afterConflictSave?: () => void;

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
     * 生成草稿后预检 sourceKey；无冲突直接保存，有冲突弹出二次确认（供 nzModal nzOnOk 订阅 Observable）。
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
            switchMap((comp) =>
                this.afterGeneratePersistPipeline(
                    [comp],
                    () => {
                        this.generateModalVisible.set(false);
                        this.helpCommandInput = '';
                        this.helpTextInput = '';
                    },
                    'cli'
                )
            ),
            catchError(e => {
                this.message.error('生成或保存失败' + e.message);
                return of(false);
            })
        ).subscribe();
    }

    confirmGenerateFromOpenApi(){
        const specUrl = this.openApiSpecUrlInput?.trim();
        const spec = this.openApiSpecInput?.trim();
        if (!specUrl && !spec) {
            this.message.warning('请填写文档链接，或粘贴 OpenAPI / Swagger 全文（JSON 或 YAML）');
            return ;
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
        this.http.post<any[]>('/bpm/component/from-openapi', body).pipe(
            switchMap((list) => {
                if (!list?.length) {
                    this.message.warning('未生成任何组件');
                    return of(false);
                }
                return this.afterGeneratePersistPipeline(
                    list,
                    () => {
                        this.openApiModalVisible.set(false);
                        this.openApiSpecUrlInput = '';
                        this.openApiSpecInput = '';
                        this.openApiBaseUrlInput = '';
                    },
                    'openapi'
                );
            }),
            catchError(e => {
                this.message.error('生成或保存失败' + e.message);
                return of(false);
            })
        ).subscribe();
    }

    /**
     * @returns 已全部保存或已打开冲突弹窗（后者返回 false，以免关闭生成来源弹窗）
     */
    private afterGeneratePersistPipeline(
        drafts: any[],
        afterSuccess: () => void,
        source: 'cli' | 'openapi'
    ): Observable<boolean> {
        return this.http
            .post<ArrayResult<BpmPreviewConflictItem>>('/bpm/component/preview-conflicts', { components: drafts })
            .pipe(
                switchMap((preview) => {
                    const previewItems : BpmPreviewConflictItem[] = preview.content || [];
                    if (!previewItems.some((x) => x.conflict)) {
                        return forkJoin(
                            drafts.map((d) =>
                                this.http.post<unknown>('/bpm/component', d, { showLoading: false })
                            )
                        ).pipe(
                            tap(() => {
                                afterSuccess();
                                this.crudPage()?.reloadTable();
                                if (source === 'cli') {
                                    this.message.success('已根据 CLI help 生成并保存组件');
                                } else {
                                    this.message.success(`已根据文档生成并保存 ${drafts.length} 个组件`);
                                }
                            }),
                            map(() => true)
                        );
                    }
                    this.pendingDrafts.set(drafts);
                    this.previewConflictItems.set(previewItems);
                    this.conflictActions.set(
                        previewItems.map((p) => (p.conflict ? this.defaultConflictAction(p) : 'cancel'))
                    );
                    this.afterConflictSave = afterSuccess;
                    this.conflictModalVisible.set(true);
                    return of(false);
                })
            );
    }

    private defaultConflictAction(p: BpmPreviewConflictItem): ConflictSaveAction {
        if (p.existingId) {
            return 'overwrite';
        }
        if (p.duplicateOfBatchIndex != null && p.duplicateOfBatchIndex >= 0) {
            return 'add';
        }
        return 'cancel';
    }

    setConflictAction(index: number, action: string): void {
        const cur = [...this.conflictActions()];
        cur[index] = action as ConflictSaveAction;
        this.conflictActions.set(cur);
    }

    closeConflictModal(): void {
        this.conflictModalVisible.set(false);
        this.afterConflictSave = undefined;
        this.pendingDrafts.set([]);
        this.previewConflictItems.set([]);
        this.conflictActions.set([]);
    }

    /**
     * 冲突弹窗确定：按每行选择执行 POST / PUT / 跳过。
     */
    confirmConflictSave() {
        const drafts = this.pendingDrafts();
        const preview = this.previewConflictItems();
        const actions = this.conflictActions();
        this.persistAccordingToPreview(drafts, preview, actions).pipe(
            tap((closedOk) => {
                if (closedOk) {
                    const cb = this.afterConflictSave;
                    this.afterConflictSave = undefined;
                    cb?.();
                    this.crudPage()?.reloadTable();
                    this.message.success('保存完成');
                }
            }),
            map((closedOk) => closedOk)
        ).subscribe();
    }

    conflictHint(p: BpmPreviewConflictItem): string {
        if (p.existingId) {
            return `库中已有：${p.existingName || p.existingId}`;
        }
        if (p.duplicateOfBatchIndex != null && p.duplicateOfBatchIndex >= 0) {
            return `与本次列表第 ${p.duplicateOfBatchIndex + 1} 条重复（尚无库记录）`;
        }
        return '';
    }

    private persistAccordingToPreview(
        drafts: any[],
        preview: BpmPreviewConflictItem[],
        actions: ConflictSaveAction[]
    ): Observable<boolean> {
        const reqs: Observable<unknown>[] = [];
        for (let i = 0; i < drafts.length; i++) {
            const p = preview[i];
            const d = drafts[i];
            if (!p.conflict) {
                reqs.push(this.http.post<unknown>('/bpm/component', d, { showLoading: false }));
                continue;
            }
            const act = actions[i];
            if (act === 'cancel') {
                continue;
            }
            if (act === 'overwrite') {
                const id = p.existingId;
                if (!id) {
                    continue;
                }
                reqs.push(
                    this.http.put<unknown>(`/bpm/component/${id}`, { ...d, id }, { showLoading: false })
                );
                continue;
            }
            if (act === 'add') {
                reqs.push(
                    this.http
                        .post<{ sourceKey: string }>('/bpm/component/allocate-source-key', {
                            parentId: d.parentId,
                            baseSourceKey: d.sourceKey,
                        })
                        .pipe(
                            switchMap((r) =>
                                this.http.post<unknown>(
                                    '/bpm/component',
                                    { ...d, sourceKey: r.sourceKey },
                                    { showLoading: false }
                                )
                            )
                        )
                );
            }
        }
        if (reqs.length === 0) {
            this.message.warning('未保存任何组件');
            return of(false);
        }
        return forkJoin(reqs).pipe(
            map(() => {
                this.conflictModalVisible.set(false);
                return true;
            }),
            catchError(() => {
                this.message.error('保存失败');
                return of(false);
            })
        );
    }

    saveParameters() {
        this.crudPage()?.reloadTable();
        this.parametersModalVisible.set(false);

    }

    /** 子组件「重新生成 command」完成后，用后端返回的最新组件刷新本地状态并提示用户 */
    onRebuildCommand(latest: any) {
        if (!latest) {
            return;
        }
        this.selectedComponent.set(latest);
        this.message.success('command 已重新生成');
    }

}
