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
import gridModule from 'diagram-js-grid';
import Create from 'diagram-js/lib/features/create/Create';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule, NzIconService } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzLayoutComponent, NzLayoutModule } from "ng-zorro-antd/layout";
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { finalize } from 'rxjs/operators';
import kiwiDescriptor from '../../component/kiwi.json';
import { ElementModel } from '../extension/element-model';
import { BpmPallete } from "../palette/pallete";
import { BpmPropertiesPanel } from '../property-panel/properties-panel';
import { ComponentProvider } from '../../component/component-provider';
import { ProcessDesignService } from '../service/process-degisn.service';
import { BpmToolbar } from "../toolbar/bpm-toolbar";

/** SpEL 表达式编辑器用到的变量推导（属性面板已接入，可按需复用） */
export { buildSpelVariableSuggestions, type SpelVariableSuggestion } from '../expression/bpm-spel-variable-context';

export abstract class BpmEditorToken {
  abstract deploy(): void;
  abstract start(): void;
  abstract save(): void;

  abstract clearSelection(): void;

  /** 将当前图另存为组件库中的组件（输入/输出由服务端分析 BPMN 推导） */
  abstract saveAsComponent(): void;

  bpmnModeler!: BpmnModeler
}


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

  elementModel = inject(ElementModel);


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
        {
          http: ['value', this.http],
        },
      ],
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

