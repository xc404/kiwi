import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/diagram-js.css';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { Element } from 'bpmn-js/lib/model/Types';
import gridModule from 'diagram-js-grid';
import { NzLayoutComponent, NzLayoutModule } from 'ng-zorro-antd/layout';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { finalize } from 'rxjs/operators';
import kiwiDescriptor from '../../flow-elements/kiwi.json';
import { ElementModel } from '../extension/element-model';
import { BpmPallete } from '../palette/pallete';
import { BpmPropertiesPanel } from '../property-panel/properties-panel';
import {
  ComponentDescription,
  ComponentProvider,
} from '../../flow-elements/component-provider';
import appendComponentModule from '../context-pad/append-component-module';
import { ProcessDesignService } from '../service/process-design.service';
import { BpmToolbar } from '../toolbar/bpm-toolbar';
import type {
  BpmSaveAsComponentModalData,
  SaveAsComponentFormPayload,
} from '../toolbar/bpm-save-as-component-modal/bpm-save-as-component-modal.component';
import { BpmEditorAppendService } from '../service/bpm-editor-append.service';
import { BpmEditorProcessMetaComponent } from './bpm-editor-process-meta/bpm-editor-process-meta.component';
import { BpmEditorToken } from './bpm-editor-token';
import { BpmStartVariablesService } from '../service/bpm-start-variables.service';
import type { BpmProcess } from '../../types/bpm-process';

export { buildSpelVariableSuggestions, type SpelVariableSuggestion } from '../expression/bpm-spel-variable-context';

export { BpmEditorToken };

@Component({
  selector: 'bpm-editor',
  templateUrl: './bpm-editor.html',
  styleUrl: './bpm-editor.scss',
  providers: [
    { provide: BpmEditorToken, useExisting: BpmEditor },
    BpmEditorAppendService,
  ],
  imports: [
    BpmPropertiesPanel,
    BpmPallete,
    NzLayoutComponent,
    NzLayoutModule,
    BpmToolbar,
    NzTabsModule,
    NzSpinModule,
    BpmEditorProcessMetaComponent,
  ],
  standalone: true,
})
export class BpmEditor implements OnInit, BpmEditorToken {
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly processDefinitionService = inject(ProcessDesignService);
  private readonly componentProvider = inject(ComponentProvider);
  private readonly elementModel = inject(ElementModel);
  private readonly append = inject(BpmEditorAppendService);
  private readonly message = inject(NzMessageService);
  private readonly startVariables = inject(BpmStartVariablesService);

  recentComponentUsages = signal<ComponentDescription[]>([]);

  bpmnModeler!: BpmnModeler<null>;
  bpmnId = signal<string>('');
  bpmProcess = signal<BpmProcess | null>(null);
  processLoading = signal(true);

  depolyVersionBehind = computed(() => {
    const p = this.bpmProcess();
    if (!p?.deployedVersion) {
      return true;
    }
    if (!p.version) {
      return false;
    }
    return p.version > p.deployedVersion;
  });

  processMeta = computed((): BpmProcess | null => this.bpmProcess());

  stackIdx = undefined;
  commandStack: any;

  constructor() {
    this.route.params.subscribe((params) => {
      this.bpmnId.set(params['id']);
    });
  }

  ngOnInit(): void {
    this.bpmnModeler = new BpmnModeler({
      container: '.canvas',
      additionalModules: [
        gridModule,
        appendComponentModule,
        { http: ['value', this.http] },
      ],
      kiwiAppendComponent: {
        getComponentGroups: () => this.componentProvider.componentGroups(),
        getRecentUsages: () => this.recentComponentUsages(),
        append: (sourceElement: Element, component: ComponentDescription, event: MouseEvent | undefined) => {
          this.append.appendComponentFromContextPad(sourceElement, component, event);
        },
      },
      moddleExtensions: {
        moddleProvider: this.elementModel.getModdleExtension(),
        componentProvider: kiwiDescriptor,
      },
    });
    this.append.init(this.bpmnModeler);
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

  dirty(): boolean {
    return this.commandStack._stackIdx !== this.stackIdx;
  }

  save(): Promise<any> {
    const stackIdx = this.commandStack._stackIdx;
    if (!this.dirty()) {
      return Promise.resolve(this.bpmProcess());
    }
    return new Promise((resolve) => {
      this.bpmnModeler.saveXML({ format: true }).then((bpmn: any) => {
        this.processDefinitionService.updateProcess(this.bpmProcess()!.id!, { bpmnXml: bpmn.xml }).subscribe(
          (data: BpmProcess) => {
            this.bpmProcess.set(data);
            this.stackIdx = stackIdx;
            this.refreshRecentComponentUsages();
            resolve(data);
          },
        );
      });
    });
  }

  clearSelection(): void {
    const selection: any = this.bpmnModeler.get('selection');
    selection.select(null);
  }

  deploy(): Promise<any> {
    return this.save().then(() => {
      if (!this.depolyVersionBehind()) {
        return Promise.resolve(this.bpmProcess());
      }
      return new Promise((resolve) => {
        this.processDefinitionService.deployProcess(this.bpmProcess()!.id!).subscribe((data: BpmProcess) => {
          this.bpmProcess.set(data);
          resolve(data);
        });
      });
    });
  }

  loadDefinition(): void {
    this.processLoading.set(true);
    this.processDefinitionService
      .getProcessById(this.bpmnId())
      .pipe(finalize(() => this.processLoading.set(false)))
      .subscribe({
        next: (data: BpmProcess) => {
          this.bpmProcess.set(data);
          this.bpmnModeler.importXML(data.bpmnXml ?? '');
        },
        error: () => {
          this.bpmProcess.set(null);
        },
      });
  }

  importBpmnXml(xml: string): Promise<void> {
    const trimmed = typeof xml === 'string' ? xml.trim() : '';
    if (!trimmed) {
      this.message.warning('BPMN XML 为空');
      return Promise.reject(new Error('empty xml'));
    }
    return this.bpmnModeler
      .importXML(trimmed)
      .then(({ warnings }) => {
        const canvas = this.bpmnModeler.get('canvas') as { zoom: (v: string) => void };
        canvas.zoom('fit-viewport');
        this.clearSelection();
        if (warnings?.length) {
          console.warn('BPMN import warnings:', warnings);
          this.message.warning(
            `已导入，存在 ${warnings.length} 条解析提示（详见控制台），请检查后保存`,
          );
        } else {
          this.message.success('已导入 BPMN XML，尚未保存到服务器');
        }
      })
      .catch((err: unknown) => {
        const msg =
          err && typeof err === 'object' && 'message' in err
            ? String((err as { message: unknown }).message)
            : String(err);
        this.message.error(`导入失败：${msg}`);
        return Promise.reject(err);
      });
  }

  start(variables?: Record<string, unknown> | string): Promise<unknown> {
    let resolved: Record<string, unknown>;
    try {
      resolved = this.startVariables.resolve(this.bpmnId(), variables);
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

  getSaveAsComponentModalDefaults(): BpmSaveAsComponentModalData {
    const process = this.bpmProcess();
    return {
      defaultName: process?.name || '流程',
      defaultDescription: '',
      defaultVersion: '1.0',
    };
  }

  submitSaveAsComponent(payload: SaveAsComponentFormPayload): Promise<void> {
    return this.deploy()
      .then(() => {
        const processId = this.bpmProcess()?.id as string;
        if (!processId) {
          this.message.error('流程未加载');
          return Promise.reject(new Error('no process'));
        }
        return firstValueFrom(
          this.processDefinitionService.saveAsComponent(processId, {
            name: payload.name,
            description: payload.description,
            version: payload.version,
          }),
        );
      })
      .then(() => {
        this.message.success('已保存到组件库');
      })
      .catch((err: unknown) => {
        const e = err as { error?: { message?: string }; message?: string };
        const msg = e?.error?.message || e?.message || '保存失败';
        this.message.error(msg);
        return Promise.reject(err);
      });
  }

  getStartProcessModalInitialText(): string {
    const id = this.bpmnId();
    const cached = id ? this.startVariables.load(id) : undefined;
    return cached !== undefined ? JSON.stringify(cached, null, 2) : '{}';
  }

  submitStartProcessFromModal(variables: Record<string, unknown>): Promise<void> {
    return this.startProcessWithVariables(variables).then(() => undefined);
  }

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
    this.append.appendComponentForAi(componentId, sourceElementId);
  }

  private startProcessWithVariables(variables: Record<string, unknown>): Promise<unknown> {
    const id = this.bpmnId();
    if (!id) {
      this.message.error('流程 ID 不存在');
      return Promise.reject(new Error('no bpmn id'));
    }
    this.startVariables.persist(id, variables);
    return this.deploy().then(
      () =>
        new Promise<unknown>((resolve, reject) => {
          this.processDefinitionService.startProcess(id, { variables }).subscribe({
            next: (data: unknown) => resolve(data),
            error: (err: unknown) => reject(err),
          });
        }),
    );
  }
}
