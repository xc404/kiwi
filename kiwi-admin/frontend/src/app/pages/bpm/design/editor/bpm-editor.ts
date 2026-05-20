import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import type { AiChatMessage } from '@services/ai-chat/ai-chat.service';
import { ChatComponent } from '@shared/components/chat/chat.component';
import type { AssistantActionHandler } from '@shared/ai-assistant/assistant-action-handler';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/diagram-js.css';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { Element } from 'bpmn-js/lib/model/Types';
import gridModule from 'diagram-js-grid';
import { NzLayoutComponent, NzLayoutModule } from 'ng-zorro-antd/layout';
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
import replaceComponentModule from '../context-pad/replace-component-module';
import customContextPadModule from '../context-pad/index';
import { ProcessDesignService } from '../service/process-design.service';
import { BpmToolbar } from '../toolbar/bpm-toolbar';
import { BpmEditorAppendService } from '../service/bpm-editor-append.service';
import { BpmEditorReplaceService } from '../service/bpm-editor-replace.service';
import { ComponentService } from '../../flow-elements/component-service';
import { BpmEditorProcessMetaComponent } from './bpm-editor-process-meta/bpm-editor-process-meta.component';
import { BpmEditorToken } from './bpm-editor-token';
import type { BpmProcess } from '../../types/bpm-process';
import { createBpmDesignerAssistantHandlers } from '../assistant/bpm-designer-assistant.handlers';
import { BpmDesignerToolbarService } from '../toolbar/bpm-designer-toolbar.service';

export { BpmExpressionVariableService } from '../expression/bpm-expression-variable.service';
export { ExpressionVariableContext } from '../expression/expression-variable-context';
export type { SpelVariableSuggestion } from '../expression/expression-variable';

export { BpmEditorToken };

@Component({
  selector: 'bpm-editor',
  templateUrl: './bpm-editor.html',
  styleUrl: './bpm-editor.scss',
  providers: [
    { provide: BpmEditorToken, useExisting: BpmEditor },
    BpmEditorAppendService,
    BpmEditorReplaceService,
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
    ChatComponent,
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
  private readonly replace = inject(BpmEditorReplaceService);
  private readonly componentService = inject(ComponentService);
  private readonly toolbarService = inject(BpmDesignerToolbarService);

  @ViewChild(BpmToolbar) private toolbar?: BpmToolbar;

  readonly bpmAssistantHandlers: AssistantActionHandler[] = createBpmDesignerAssistantHandlers(
    this,
    () => this.toolbar,
  );

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
        customContextPadModule,
        appendComponentModule,
        replaceComponentModule,
        { http: ['value', this.http] },
      ],
      kiwiAppendComponent: {
        getComponentGroups: () => this.componentProvider.componentGroups(),
        getRecentUsages: () => this.recentComponentUsages(),
        append: (sourceElement: Element, component: ComponentDescription, event: MouseEvent | undefined) => {
          this.append.appendComponentFromContextPad(sourceElement, component, event);
        },
      },
      kiwiReplaceComponent: {
        getComponentGroups: () => this.componentProvider.componentGroups(),
        getRecentUsages: () => this.recentComponentUsages(),
        getCurrentComponentId: (element: Element) =>
          this.componentService.getComponentForElement(element)?.id,
        replace: (element: Element, component: ComponentDescription) => {
          this.replace.replaceComponentFromContextPad(element, component);
        },
      },
      moddleExtensions: {
        moddleProvider: this.elementModel.getModdleExtension(),
        componentProvider: kiwiDescriptor,
      },
    });
    this.append.init(this.bpmnModeler);
    this.replace.init(this.bpmnModeler);
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

  getBpmnId(): string {
    return this.bpmnId();
  }

  getBpmProcess(): BpmProcess | null {
    return this.bpmProcess();
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

  runToolbarCommand(command: string, options?: Record<string, unknown>): void {
    if (!this.toolbar) {
      throw new Error('工具栏未就绪');
    }
    this.toolbarService.run(command, this.toolbar, options);
  }

  enrichDesignerMessages = (messages: AiChatMessage[]): Promise<AiChatMessage[]> => {
    return this.buildDesignerContextMessage().then((ctx) => [ctx, ...messages]);
  };

  private async buildDesignerContextMessage(): Promise<AiChatMessage> {
    const process = this.bpmProcess();
    const processId = this.bpmnId() || process?.id || '';
    let xml = process?.bpmnXml ?? '';
    if (this.bpmnModeler) {
      try {
        const saved = await this.bpmnModeler.saveXML({ format: false });
        if (saved.xml) {
          xml = saved.xml;
        }
      } catch {
        /* 保留流程定义上的 XML */
      }
    }
    const maxLen = 48_000;
    let xmlBlock = xml;
    if (xmlBlock.length > maxLen) {
      xmlBlock = `${xmlBlock.slice(0, maxLen)}\n<!-- …已截断，完整图请保存后重试… -->`;
    }
    const selectedId = this.getSelectedElementId();
    const lines = [
      '你正在 Kiwi BPM 流程设计器中协助用户。请结合下列上下文回答；若需改图，请通过 assistant_designer_* 工具登记客户端动作。',
      `processId: ${processId}`,
      process?.name ? `processName: ${process.name}` : '',
      selectedId ? `selectedElementId: ${selectedId}` : 'selectedElementId: （无单选元素）',
      `可用 toolbar 命令: ${this.toolbarService.listAiCommandIds().join(', ')}`,
      'appendComponent 参数: componentId（组件库 id）、sourceElementId（可选）',
      '当前 BPMN XML:',
      '```xml',
      xmlBlock || '（空）',
      '```',
    ].filter(Boolean);
    return { role: 'system', content: lines.join('\n') };
  }

  private getSelectedElementId(): string | null {
    if (!this.bpmnModeler) {
      return null;
    }
    const selection = this.bpmnModeler.get('selection') as { get: () => Element[] };
    const selected = selection.get?.() ?? [];
    if (selected.length !== 1) {
      return null;
    }
    return selected[0].id ?? null;
  }

}
