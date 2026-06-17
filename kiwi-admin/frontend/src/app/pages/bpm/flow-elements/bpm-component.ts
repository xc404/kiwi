import { AfterViewInit, Component, computed, inject, signal, TemplateRef, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { ArrayResult } from '@app/core/services/types';
import { AddAction, toolbarAction } from '@app/shared/components/crud/actions';
import { CrudPage } from '@app/shared/components/crud/components/crud-page';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { ColumnToken } from '@app/shared/components/table/column';
import { DictSelector } from '@shared/components/dict-selector/dict-selector';
import { ModalDragDirective } from '@shared/modal/modal-drag.directive';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';

import { BpmInputOutputParameters } from './bpm-parameters';
import { ComponentProvider } from './component-provider';

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
  styles: [
    `
      .cli-help-hint {
        margin: 0 0 12px;
        color: rgba(0, 0, 0, 0.65);
        font-size: 13px;
        line-height: 1.5;
      }
      .conflict-row {
        margin-bottom: 16px;
        padding-bottom: 12px;
        border-bottom: 1px solid #f0f0f0;
      }
      .conflict-row:last-child {
        border-bottom: none;
      }
    `
  ],
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
    NzCheckboxModule,
    NzEmptyModule,
    NzSelectModule,
    NzSpinModule,
    DictSelector,
    ModalDragDirective
  ]
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

  jdbcSchemaModalVisible = signal(false);
  jdbcConnectionId: string | null = null;
  jdbcTables = signal<Array<{ name: string; comment?: string }>>([]);
  jdbcSelectedTableNames = signal<string[]>([]);
  jdbcTablesLoading = signal(false);

  elementTemplateModalVisible = signal(false);
  elementTemplateInput = '';
  elementTemplateInheritHttpRequest = signal(true);
  elementTemplateParentId = signal<string | null>(null);

  crudPage = viewChild(CrudPage);
  /** 工具栏「生成组件」下拉，于 ngAfterViewInit 绑定到 toolbarActions */
  generateDropdownTpl = viewChild<TemplateRef<void>>('generateDropdownTpl');
  http = inject(BaseHttpService);
  message = inject(NzMessageService);
  componentProvider = inject(ComponentProvider);

  parentComponentOptions = computed(() =>
    this.componentProvider.components().map(c => ({
      label: c.name,
      value: c.id
    }))
  );

  pageConfig = computed(() => {
    return {
      title: '组件管理',
      initializeData: true,
      crud: '/bpm/component',
      toolbarActions: [
        AddAction,
        toolbarAction({
          name: '生成组件',
          icon: 'down',
          tooltip: '从命令行、OpenAPI、JDBC 生成，或从 Camunda 8 Element Template 导入',
          template: this.generateDropdownTpl()
        })
      ],
      columnActions: [
        {
          name: '参数设置',
          handler: () => {
            const item = inject(ColumnToken).getRecord();
            this.selectedComponent.set(item);
            this.parametersModalVisible.set(true);
          }
        },
        {
          name: '导出 Camunda 8 Template',
          handler: () => {
            const item = inject(ColumnToken).getRecord();
            this.exportElementTemplate(item);
          }
        }
      ],
      fields: [
        { name: '名称', dataIndex: 'name' },
        { name: '父级ID', dataIndex: 'parentId', editor: 'component-selector' },
        { name: '来源', dataIndex: 'source' },
        { name: '描述', dataIndex: 'description' },
        { name: '分组', dataIndex: 'group' },
        { name: '类型', dataIndex: 'type' }
      ]
    };
  });

  constructor() {}

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

  openGenerateFromJdbcSchema(): void {
    this.jdbcConnectionId = null;
    this.jdbcTables.set([]);
    this.jdbcSelectedTableNames.set([]);
    this.jdbcSchemaModalVisible.set(true);
  }

  openImportFromElementTemplate(): void {
    this.elementTemplateInput = '';
    this.elementTemplateInheritHttpRequest.set(true);
    this.elementTemplateParentId.set(null);
    this.elementTemplateModalVisible.set(true);
  }

  onElementTemplateFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    file
      .text()
      .then(text => {
        this.elementTemplateInput = text;
      })
      .catch(() => {
        this.message.error('读取 JSON 文件失败');
      });
  }

  confirmImportFromElementTemplate() {
    const template = this.elementTemplateInput?.trim();
    if (!template) {
      this.message.warning('请粘贴 Camunda 8 Element Template JSON，或上传 .json 文件');
      return;
    }
    const body: { template: string; inheritHttpRequest: boolean; parentId?: string } = {
      template,
      inheritHttpRequest: this.elementTemplateInheritHttpRequest()
    };
    if (!this.elementTemplateInheritHttpRequest()) {
      const parentId = this.elementTemplateParentId()?.trim();
      if (parentId) {
        body.parentId = parentId;
      }
    }
    this.http
      .post<ArrayResult<any>>('/bpm/component/from-element-template', body)
      .pipe(
        switchMap(res => {
          const list = this.normalizeComponentList(res);
          if (!list.length) {
            this.message.warning('未解析到任何组件');
            return of(false);
          }
          return this.afterGeneratePersistPipeline(
            list,
            () => {
              this.elementTemplateModalVisible.set(false);
              this.elementTemplateInput = '';
              this.elementTemplateInheritHttpRequest.set(true);
              this.elementTemplateParentId.set(null);
            },
            'element-template'
          );
        }),
        catchError(e => {
          this.message.error(`导入或保存失败${e.message ?? ''}`);
          return of(false);
        })
      )
      .subscribe();
  }

  exportElementTemplate(record: { id?: string; sourceKey?: string; key?: string; name?: string }): void {
    const id = record?.id?.trim();
    if (!id) {
      this.message.warning('组件 id 无效');
      return;
    }
    this.http
      .get<string>(`/bpm/component/${id}/element-template`)
      .pipe(
        catchError(e => {
          this.message.error(`导出失败${e.message ?? ''}`);
          return of('');
        })
      )
      .subscribe(json => {
        if (!json) {
          return;
        }
        const baseName = (record.sourceKey || record.key || record.name || 'component').replace(/[^\w.-]+/g, '_');
        const blob = new Blob([json], { type: 'application/json;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${baseName}.element-template.json`;
        anchor.click();
        URL.revokeObjectURL(url);
        this.message.success('已导出 Camunda 8 Element Template');
      });
  }

  onJdbcConnectionChange(connectionId: string | null): void {
    this.jdbcConnectionId = connectionId;
    this.jdbcSelectedTableNames.set([]);
    this.loadJdbcTables();
  }

  loadJdbcTables(): void {
    const connId = this.jdbcConnectionId;
    if (!connId) {
      this.jdbcTables.set([]);
      return;
    }
    this.jdbcTablesLoading.set(true);
    this.http
      .get<{ content?: Array<{ name: string; comment?: string }> }>(`tools/connection/${connId}/tables`, undefined, {
        showLoading: false
      })
      .pipe(
        catchError(e => {
          this.message.error(`加载表列表失败${e.message ?? ''}`);
          return of({ content: [] as Array<{ name: string; comment?: string }> });
        })
      )
      .subscribe(res => {
        this.jdbcTables.set(res.content ?? []);
        this.jdbcTablesLoading.set(false);
      });
  }

  toggleJdbcTable(name: string, checked: boolean): void {
    const cur = [...this.jdbcSelectedTableNames()];
    if (checked) {
      if (!cur.includes(name)) {
        cur.push(name);
      }
    } else {
      const idx = cur.indexOf(name);
      if (idx >= 0) {
        cur.splice(idx, 1);
      }
    }
    this.jdbcSelectedTableNames.set(cur);
  }

  confirmGenerateFromJdbcSchema() {
    const connectionId = this.jdbcConnectionId?.trim();
    const tables = this.jdbcSelectedTableNames();
    if (!connectionId) {
      this.message.warning('请选择 JDBC 连接');
      return;
    }
    if (!tables.length) {
      this.message.warning('请至少选择一张表');
      return;
    }
    this.http
      .post<any[]>('/bpm/component/from-jdbc-schema', { connectionId, tables })
      .pipe(
        switchMap(list => {
          if (!list?.length) {
            this.message.warning('未生成任何组件');
            return of(false);
          }
          return this.afterGeneratePersistPipeline(
            list,
            () => {
              this.jdbcSchemaModalVisible.set(false);
              this.jdbcConnectionId = null;
              this.jdbcTables.set([]);
              this.jdbcSelectedTableNames.set([]);
            },
            'dbschema'
          );
        }),
        catchError(e => {
          this.message.error(`生成或保存失败${e.message}`);
          return of(false);
        })
      )
      .subscribe();
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
    this.http
      .post<any>('/bpm/component/from-cli-help', body)
      .pipe(
        switchMap(comp =>
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
          this.message.error(`生成或保存失败${e.message}`);
          return of(false);
        })
      )
      .subscribe();
  }

  confirmGenerateFromOpenApi() {
    const specUrl = this.openApiSpecUrlInput?.trim();
    const spec = this.openApiSpecInput?.trim();
    if (!specUrl && !spec) {
      this.message.warning('请填写文档链接，或粘贴 OpenAPI / Swagger 全文（JSON 或 YAML）');
      return;
    }
    const baseUrl = this.openApiBaseUrlInput?.trim();
    const body: { spec?: string; specUrl?: string; baseUrl?: string } = {
      baseUrl: baseUrl || undefined
    };
    if (specUrl) {
      body.specUrl = specUrl;
    }
    if (spec) {
      body.spec = spec;
    }
    this.http
      .post<any[]>('/bpm/component/from-openapi', body)
      .pipe(
        switchMap(list => {
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
          this.message.error(`生成或保存失败${e.message}`);
          return of(false);
        })
      )
      .subscribe();
  }

  /**
   * @returns 已全部保存或已打开冲突弹窗（后者返回 false，以免关闭生成来源弹窗）
   */
  private normalizeComponentList(res: any[] | ArrayResult<any> | null | undefined): any[] {
    if (!res) {
      return [];
    }
    if (Array.isArray(res)) {
      return res;
    }
    return res.content ?? [];
  }

  private afterGeneratePersistPipeline(drafts: any[], afterSuccess: () => void, source: 'cli' | 'openapi' | 'dbschema' | 'element-template'): Observable<boolean> {
    return this.http.post<ArrayResult<BpmPreviewConflictItem>>('/bpm/component/preview-conflicts', { components: drafts }).pipe(
      switchMap(preview => {
        const previewItems: BpmPreviewConflictItem[] = preview.content || [];
        if (!previewItems.some(x => x.conflict)) {
          return forkJoin(drafts.map(d => this.http.post<unknown>('/bpm/component', d, { showLoading: false }))).pipe(
            tap(() => {
              afterSuccess();
              this.crudPage()?.reloadTable();
              if (source === 'cli') {
                this.message.success('已根据 CLI help 生成并保存组件');
              } else if (source === 'dbschema') {
                this.message.success(`已根据表结构生成并保存 ${drafts.length} 个组件`);
              } else if (source === 'element-template') {
                this.message.success(`已从 Camunda 8 Template 导入并保存 ${drafts.length} 个组件`);
              } else {
                this.message.success(`已根据文档生成并保存 ${drafts.length} 个组件`);
              }
            }),
            map(() => true)
          );
        }
        this.pendingDrafts.set(drafts);
        this.previewConflictItems.set(previewItems);
        this.conflictActions.set(previewItems.map(p => (p.conflict ? this.defaultConflictAction(p) : 'cancel')));
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
    this.persistAccordingToPreview(drafts, preview, actions)
      .pipe(
        tap(closedOk => {
          if (closedOk) {
            const cb = this.afterConflictSave;
            this.afterConflictSave = undefined;
            cb?.();
            this.crudPage()?.reloadTable();
            this.message.success('保存完成');
          }
        }),
        map(closedOk => closedOk)
      )
      .subscribe();
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

  private persistAccordingToPreview(drafts: any[], preview: BpmPreviewConflictItem[], actions: ConflictSaveAction[]): Observable<boolean> {
    const reqs: Array<Observable<unknown>> = [];
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
        reqs.push(this.http.put<unknown>(`/bpm/component/${id}`, { ...d, id }, { showLoading: false }));
        continue;
      }
      if (act === 'add') {
        reqs.push(
          this.http
            .post<{ sourceKey: string }>('/bpm/component/allocate-source-key', {
              baseSourceKey: d.sourceKey
            })
            .pipe(switchMap(r => this.http.post<unknown>('/bpm/component', { ...d, sourceKey: r.sourceKey }, { showLoading: false })))
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
