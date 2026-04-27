import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, ElementRef, inject, OnInit, signal, TemplateRef, ViewChild } from '@angular/core';
import { JsonCodeEditorComponent } from '@app/shared/components/json-code-editor/json-code-editor.component';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/diagram-js.css'; // 左边工具栏以及编辑节点的样式
import BpmnFactory from 'bpmn-js/lib/features/modeling/BpmnFactory';
import ElementFactory from 'bpmn-js/lib/features/modeling/ElementFactory';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { Element } from 'bpmn-js/lib/model/Types';
import gridModule from 'diagram-js-grid';
import Create from 'diagram-js/lib/features/create/Create';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule, NzIconService } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzLayoutComponent, NzLayoutModule } from "ng-zorro-antd/layout";
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { finalize } from 'rxjs/operators';
import kiwiDescriptor from '../../flow-elements/kiwi.json';
import { ElementModel } from '../extension/element-model';
import { BpmPallete } from "../palette/pallete";
import { BpmPropertiesPanel } from '../property-panel/properties-panel';
import {
  ComponentDescription,
  ComponentProvider,
} from '../../flow-elements/component-provider';
import { ComponentService } from '../../flow-elements/component-service';
import appendComponentModule from '../context-pad/append-component-module';
import { ProcessDesignService } from '../service/process-degisn.service';
import { BpmToolbar } from "../toolbar/bpm-toolbar";
import { BpmEditorToken } from './bpm-editor-token';

/** SpEL 表达式编辑器用到的变量推导（属性面板已接入，可按需复用） */
export { buildSpelVariableSuggestions, type SpelVariableSuggestion } from '../expression/bpm-spel-variable-context';

export { BpmEditorToken };


@Component({
  selector: 'bpm-editor',
  templateUrl: './bpm-editor.html',
  styleUrl: './bpm-editor.scss',
  providers: [
    {
      provide: BpmEditorToken, useExisting: BpmEditor,
    }
  ],
  imports: [
    DatePipe,
    BpmPropertiesPanel,
    NzModalModule,
    NzInputModule,
    FormsModule,
    BpmPallete,
    NzLayoutComponent,
    NzLayoutModule,
    BpmToolbar,
    NzTabsModule,
    NzSpinModule,
    NzButtonModule,
    NzIconModule,
    JsonCodeEditorComponent,
  ],
  standalone: true,
})
export class BpmEditor implements OnInit, BpmEditorToken {

  @ViewChild('startJsonEditor') private startJsonEditor?: JsonCodeEditorComponent;

  acvitedRoute = inject(ActivatedRoute);

  http = inject(HttpClient);
  processDefinitionService = inject(ProcessDesignService)
  matDialog = inject(NzModalService);
  componentProvider = inject(ComponentProvider);
  componentService = inject(ComponentService);

  elementModel = inject(ElementModel);

  /** 从已保存流程解析的最近使用组件（GET /bpm/component/recent-usage），结构与组件库一致 */
  recentComponentUsages = signal<ComponentDescription[]>([]);

  bpmnModeler!: BpmnModeler<null>;

  bpmnId = signal<string>('');
  bpmProcess = signal<any>(null);
  /** 流程详情请求进行中 */
  processLoading = signal(true);

  iconService = inject(NzIconService);
  private readonly message = inject(NzMessageService);

  @ViewChild('#canvas') canvas: ElementRef | undefined;
  protected readonly title = signal('bpm-frontend');

  @ViewChild('processNameDialog')
  processNameDialog!: TemplateRef<any>;

  processName: any;

  /** 另存为组件：nz-modal 可见性 */
  saveAsComponentModalVisible = signal(false);

  /** 另存为组件：与后端 SaveAsComponentInput 一致（name / description / version） */
  saveAsComponentName = '';
  saveAsComponentDescription = '';
  saveAsComponentVersion = '1.0';
  autoSave = false;

  /** 启动流程：编辑变量 JSON（nz-modal） */
  startProcessModalVisible = signal(false);
  /** 与后端流程变量一致：须为 JSON 对象（非数组） */
  startProcessVariablesJson = '{}';

  private static readonly START_VARS_LS_PREFIX = 'kiwi.bpm.editor.startVariables.v1';

  /** 后端 BaseEntity 为 updatedTime；兼容旧字段 updatedAt */
  updatedAt = computed(() => {
    const p = this.bpmProcess();
    return p?.updatedTime ?? p?.updatedAt;
  });

  delopyAt = computed(() => {
    return this.bpmProcess()?.deployedAt;
  });

  /** 顶栏展示用元信息（与接口字段对齐） */
  processMeta = computed(() => {
    const p = this.bpmProcess();
    if (!p) {
      return null;
    }
    return {
      id: p.id as string | undefined,
      name: (p.name as string | undefined) ?? '—',
      projectId: p.projectId as string | undefined,
      updatedTime: (p.updatedTime ?? p.updatedAt) as string | Date | undefined,
      deployedAt: p.deployedAt as string | Date | undefined,
      version: p.version as number | undefined,
      deployedVersion: p.deployedVersion as number | undefined,
    };
  });
  bpmnFactory!: BpmnFactory;
  create!: Create;
  elementFactory!: ElementFactory;

  depolyVersionBehind = computed(() => {
    if (!this.bpmProcess()?.deployedVersion) {
      return true;
    }
    if (!this.bpmProcess()?.version) {
      return false;
    }
    return this.bpmProcess()?.version > this.bpmProcess()?.deployedVersion;
  });
  stackIdx = undefined;
  commandStack: any;

  constructor() {
    this.acvitedRoute.params.subscribe(params => {
      this.bpmnId.set(params['id']);
    });
  }

  ngOnInit(): void {

    this.bpmnModeler = new BpmnModeler({
      container: ".canvas",
      additionalModules: [
        gridModule,
        appendComponentModule,
        {
          http: ['value', this.http],
        },
      ],
      kiwiAppendComponent: {
        getComponentGroups: () => this.componentProvider.componentGroups(),
        getRecentUsages: () => this.recentComponentUsages(),
        append: (sourceElement: Element, component: ComponentDescription, event: MouseEvent | undefined) => {
          this.appendComponentFromContextPad(sourceElement, component, event);
        },
      },
      moddleExtensions: {
        moddleProvider: this.elementModel.getModdleExtension(),
        componentProvider: kiwiDescriptor
      }
    })
    this.bpmnFactory = this.bpmnModeler.get('bpmnFactory');
    this.create = this.bpmnModeler.get('create');
    this.elementFactory = this.bpmnModeler.get('elementFactory');
    this.commandStack = this.bpmnModeler.get('commandStack');

    this.loadDefinition();

    this.processDefinitionService.getRecentComponentUsages().subscribe({
      next: (list) =>
        this.recentComponentUsages.set(
          (list ?? []).map((c) => ({
            ...c,
            icon: c.icon || 'bpmn-icon-service-task',
          })),
        ),
      error: () => this.recentComponentUsages.set([]),
    });
  }

  dirty() {

    return this.commandStack._stackIdx !== this.stackIdx;
  }

  // 下载为SVG格式,done是个函数，调用的时候传入的
  saveSVG() {
    // 把传入的done再传给bpmn原型的saveSVG函数调用
    this.bpmnModeler.saveSVG();
  }
  save() {
    let stackIdx = this.commandStack._stackIdx;
    if (!this.dirty()) {
      return Promise.resolve(this.bpmProcess());
    }
    return new Promise((resolve) => {
      this.bpmnModeler.saveXML({ format: true }).then((bpmn: any) => {
        this.processDefinitionService.updateProcess(this.bpmProcess().id, {
          bpmnXml: bpmn.xml
        }).subscribe(
          (data: any) => {
            this.bpmProcess.set(data)
            this.stackIdx = stackIdx;
            this.refreshRecentComponentUsages();
            resolve(data)
          }
        );
      });
    });

  }

  clearSelection() {
    let selection: any = this.bpmnModeler.get('selection');
    selection.select(null);
  }
  saveAsDefinition() {

    // this.matDialog.create({
    //   nzContent: this.processNameDialog
    // }).afterClosed().subscribe((result: any) => {
    //   if (result) {
    //     this.bpmnModeler.saveXML({ format: true }).then((bpmn: any) => {
    //       this.processDefinitionService.saveAsProcess(this.bpmProcess().id, this.processName, bpmn.xml).subscribe(
    //         (data: any) => {
    //           this.bpmProcess.set(data)
    //         }
    //       );
    //     });
    //   }
    // });
  }

  deploy() {
    return this.save().then(() => {
      if (!this.depolyVersionBehind()) {
        return Promise.resolve(this.bpmProcess());
      }
      return new Promise((resolve) => {
        this.processDefinitionService.deployProcess(this.bpmProcess().id).subscribe(
          (data: any) => {
            this.bpmProcess.set(data)
            resolve(data)
          }
        );
      });
    });
  };



  loadDefinition() {
    this.processLoading.set(true);
    this.processDefinitionService
      .getProcessById(this.bpmnId())
      .pipe(finalize(() => this.processLoading.set(false)))
      .subscribe({
        next: (data: any) => {
          this.bpmProcess.set(data);
          this.bpmnModeler.importXML(this.bpmProcess().bpmnXml);
        },
        error: () => {
          this.bpmProcess.set(null);
        },
      });
  }

  openStartProcessDialog(): void {
    const id = this.bpmnId();
    const cached = id ? this.loadStartVariablesFromStorage(id) : undefined;
    this.startProcessVariablesJson =
      cached !== undefined ? JSON.stringify(cached, null, 2) : '{}';
    this.startProcessModalVisible.set(true);
  }

  /**
   * 启动流程：可选传入变量对象或 JSON 字符串；不传则使用 localStorage 中该 bpmnId 的上次变量（无则 {}）。
   * 启动成功后会把本次使用的变量写回 localStorage。
   */
  start(variables?: Record<string, unknown> | string): Promise<unknown> {
    let resolved: Record<string, unknown>;
    try {
      resolved = this.resolveStartVariablesInput(variables);
    } catch (e) {
      if ((e as Error)?.message === 'INVALID_SHAPE') {
        this.message.warning('启动变量须为 JSON 对象');
      } else {
        this.message.error('启动变量 JSON 无效');
      }
      return Promise.reject(e);
    }
    return this.startProcessWithVariables(resolved);
  }

  /** nz-modal 确定：解析编辑器中的 JSON 并启动 */
  onStartProcessOk(): Promise<void> | boolean {
    const raw =
      this.startJsonEditor?.getDocumentText() ?? this.startProcessVariablesJson ?? '';
    const trimmed = raw.trim();
    let parsed: Record<string, unknown>;
    try {
      const raw = trimmed === '' ? {} : JSON.parse(trimmed);
      if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
        this.message.warning('须为 JSON 对象，例如 {} 或 {"key":"value"}');
        return false;
      }
      parsed = raw as Record<string, unknown>;
    } catch {
      this.message.error('JSON 格式无效');
      return false;
    }
    return this.startProcessWithVariables(parsed)
      .then(() => {
        this.startProcessModalVisible.set(false);
        this.message.success('流程已启动');
      })
      .catch((err: unknown) => {
        const e = err as { error?: { message?: string }; message?: string };
        this.message.error(e?.error?.message ?? e?.message ?? '启动失败');
        return Promise.reject(err);
      });
  }

  private startVariablesStorageKey(processId: string): string {
    return `${BpmEditor.START_VARS_LS_PREFIX}:${processId}`;
  }

  private loadStartVariablesFromStorage(processId: string): Record<string, unknown> | undefined {
    if (!processId || typeof localStorage === 'undefined') {
      return undefined;
    }
    try {
      const raw = localStorage.getItem(this.startVariablesStorageKey(processId));
      if (raw == null || raw === '') {
        return undefined;
      }
      const parsed = JSON.parse(raw) as unknown;
      if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>;
      }
      return undefined;
    } catch {
      return undefined;
    }
  }

  private persistStartVariablesToStorage(processId: string, variables: Record<string, unknown>): void {
    if (!processId || typeof localStorage === 'undefined') {
      return;
    }
    try {
      localStorage.setItem(this.startVariablesStorageKey(processId), JSON.stringify(variables));
    } catch {
      /* quota or private mode */
    }
  }

  private resolveStartVariablesInput(input?: Record<string, unknown> | string): Record<string, unknown> {
    if (input === undefined) {
      return this.loadStartVariablesFromStorage(this.bpmnId()) ?? {};
    }
    if (typeof input === 'string') {
      const trimmed = input.trim();
      if (trimmed === '') {
        return {};
      }
      const parsed = JSON.parse(trimmed) as unknown;
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        throw new Error('INVALID_SHAPE');
      }
      return parsed as Record<string, unknown>;
    }
    if (Array.isArray(input)) {
      throw new Error('INVALID_SHAPE');
    }
    return input;
  }

  private startProcessWithVariables(variables: Record<string, unknown>): Promise<unknown> {
    const id = this.bpmnId();
    if (!id) {
      this.message.error('流程 ID 不存在');
      return Promise.reject(new Error('no bpmn id'));
    }
    return this.deploy().then(
      () =>
        new Promise<unknown>((resolve, reject) => {
          this.processDefinitionService.startProcess(id, { variables }).subscribe({
            next: (data: unknown) => {
              this.persistStartVariablesToStorage(id, variables);
              console.log(data);
              resolve(data);
            },
            error: (err: unknown) => reject(err),
          });
        }),
    );
  }

  saveAsComponent(): void {
    const p = this.bpmProcess();
    const baseName = (p?.name as string) || '流程';
    this.saveAsComponentName = `${baseName}`;
    this.saveAsComponentDescription = p?.description as string || '';
    this.saveAsComponentVersion = '1.0';
    this.saveAsComponentModalVisible.set(true);
  }

  /** nz-modal 确定：校验通过后关闭弹窗并提交；返回 false 可阻止关闭（校验失败时） */
  onSaveAsComponentOk(): boolean {
    const name = this.saveAsComponentName?.trim();
    const description = this.saveAsComponentDescription?.trim();
    const version = this.saveAsComponentVersion?.trim();
    if (!name) {
      this.message.warning('请填写 name（组件名称）');
      return false;
    }

    void this.deploy().then(() => {
      const processId = this.bpmProcess()?.id as string;
      if (!processId) {
        this.message.error('流程未加载');
        return;
      }
      this.processDefinitionService
        .saveAsComponent(processId, {
          name,
          description: description || undefined,
          version: version || undefined,
        })
        .subscribe({
          next: () => {
            this.message.success('已保存到组件库');
            this.saveAsComponentModalVisible.set(false);
          },
          error: (err) => {
            const msg = err?.error?.message || err?.message || '保存失败';
            this.message.error(msg);
          },
        });
    });
    return true;
  }

  /**
   * 上下文菜单「追加业务组件」：与左侧组件面板相同的创建与 initElement 逻辑，并作为后继节点连接。
   */
  /** 保存成功后刷新「最近使用」列表（服务端从已保存 BPMN 解析） */
  private refreshRecentComponentUsages(): void {
    this.processDefinitionService.getRecentComponentUsages().subscribe({
      next: (list) =>
        this.recentComponentUsages.set(
          (list ?? []).map((c) => ({
            ...c,
            icon: c.icon || 'bpmn-icon-service-task',
          })),
        ),
      error: () => { },
    });
  }

  appendComponentForAi(componentId: string, sourceElementId?: string | null): void {
    const component = this.componentProvider.getComponent(componentId);
    if (!component) {
      this.message.error('组件库中不存在该组件，请刷新组件列表后重试');
      return;
    }
    const registry = this.bpmnModeler.get('elementRegistry') as {
      get: (id: string) => Element | undefined;
    };
    const selection = this.bpmnModeler.get('selection') as { get: () => Element[] };
    const canvas = this.bpmnModeler.get('canvas') as { getRootElement: () => Element };
    let source: Element | undefined;
    if (sourceElementId) {
      source = registry.get(sourceElementId);
    }
    if (!source) {
      const sel = selection.get();
      source = sel?.[0] ?? canvas.getRootElement();
    }
    this.appendComponentFromContextPad(source, component, undefined);
  }

  appendComponentFromContextPad(
    sourceElement: Element,
    component: ComponentDescription,
    event: MouseEvent | undefined,
  ): void {
    const item = this.componentService.convertComponentToPalette(component);
    const { type, options } = this.componentService.getElementOptions(item);
    const businessObject = this.bpmnFactory.create(type, options);
    const shape = this.elementFactory.createShape({ type, businessObject });
    this.componentService.initElement(this.bpmnModeler, shape, item);
    const autoPlace = this.bpmnModeler.get('autoPlace', false) as
      | { append: (source: Element, newShape: Element) => void }
      | false;
    if (autoPlace) {
      autoPlace.append(sourceElement, shape);
    } else if (event) {
      this.create.start(event, shape, { source: sourceElement });
    }
  }

  onDrop(event: any) {
    console.log('Drop event:', event);
    // this.createElement({} as ComponentDescription, event.event);
  }

  copyProcessId(id: string): void {
    if (!id) {
      return;
    }
    const ok = () => this.message.success('已复制到剪贴板');
    const fail = () => this.message.error('复制失败');
    if (navigator.clipboard?.writeText) {
      void navigator.clipboard.writeText(id).then(ok).catch(() => {
        try {
          const ta = document.createElement('textarea');
          ta.value = id;
          ta.style.position = 'fixed';
          ta.style.left = '-9999px';
          document.body.appendChild(ta);
          ta.select();
          document.execCommand('copy');
          document.body.removeChild(ta);
          ok();
        } catch {
          fail();
        }
      });
    } else {
      try {
        const ta = document.createElement('textarea');
        ta.value = id;
        ta.style.position = 'fixed';
        ta.style.left = '-9999px';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
        ok();
      } catch {
        fail();
      }
    }
  }

}

