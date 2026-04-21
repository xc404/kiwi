import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, ElementRef, inject, OnInit, signal, TemplateRef, ViewChild } from '@angular/core';
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
import kiwiDescriptor from '../../component/kiwi.json';
import { ElementModel } from '../extension/element-model';
import { BpmPallete } from "../palette/pallete";
import { BpmPropertiesPanel } from '../property-panel/properties-panel';
import {
  ComponentDescription,
  ComponentProvider,
} from '../../component/component-provider';
import { ComponentService } from '../../component/component-service';
import appendComponentModule from '../context-pad/append-component-module';
import { ProcessDesignService } from '../service/process-degisn.service';
import { BpmToolbar } from "../toolbar/bpm-toolbar";
import { BpmDesignerChatComponent } from './bpm-designer-chat.component';
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
    BpmDesignerChatComponent,
    NzTabsModule,
    NzSpinModule,
    NzButtonModule,
    NzIconModule,
  ],
  standalone: true,
})
export class BpmEditor implements OnInit, BpmEditorToken {


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
    if (!this.bpmProcess()?.version) {
      return false;
    }
    if (!this.bpmProcess()?.deployedVersion) {
      return true;
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
        }        ).subscribe(
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

  start() {
    return this.deploy().then(() => {
      return new Promise((resolve) => {
        this.processDefinitionService.startProcess(this.bpmnId()).subscribe(
          (data: any) => {
            console.log(data);
            resolve(data);
          }
        );
      });
    });
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
      error: () => {},
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

