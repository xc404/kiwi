import { HttpClient } from '@angular/common/http';
import { Component, computed, DestroyRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/diagram-js.css';
import { finalize } from 'rxjs/operators';

import BpmnModeler from 'bpmn-js/lib/Modeler';
import type { Element } from 'bpmn-js/lib/model/Types';
import gridModule from 'diagram-js-grid';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';

import { BpmEditorToken } from './bpm-editor-token';
import { ComponentDescription, ComponentProvider } from '../../flow-elements/component-provider';
import { ComponentService } from '../../flow-elements/component-service';
import kiwiDescriptor from '../../flow-elements/kiwi.json';
import type { BpmProcess } from '../../types/bpm-process';
import appendComponentModule from '../context-pad/append-component-module';
import customContextPadModule from '../context-pad/index';
import replaceComponentModule from '../context-pad/replace-component-module';
import { BpmDesignerContextService } from '../bpm-designer-context.service';
import { ElementModel } from '../extension/element-model';
import { BpmPallete } from '../palette/pallete';
import { BpmPropertiesPanel } from '../property-panel/properties-panel';
import { BpmEditorAppendService } from '../service/bpm-editor-append.service';
import { BpmEditorReplaceService } from '../service/bpm-editor-replace.service';
import { ProcessDesignService } from '../service/process-design.service';
import { importBpmnXmlToModeler } from '../toolbar/bpm-canvas-import.utils';
import { BpmToolbar } from '../toolbar/bpm-toolbar';
import { BpmDesignerAgentComponent } from '../agent/bpm-designer-agent.component';
import { BpmEditorProcessMetaComponent } from './bpm-editor-process-meta/bpm-editor-process-meta.component';
import type { BpmDesignerToolbarContext } from '../toolbar/bpm-designer-toolbar.types';

export { BpmExpressionVariableService } from '../expression/bpm-expression-variable.service';
export { ExpressionVariableContext } from '../expression/expression-variable-context';
export type { SpelVariableSuggestion } from '../expression/expression-variable';

export { BpmEditorToken };

const StorageKeyPaletteCollapsed = 'bpm-editor.paletteCollapsed';
const StorageKeyPropertiesCollapsed = 'bpm-editor.propertiesCollapsed';
const StorageKeyAgentCollapsed = 'bpm-editor.agentCollapsed';
const StorageKeyPaletteWidth = 'bpm-editor.paletteWidth';
const StorageKeyPropertiesWidth = 'bpm-editor.propertiesWidth';
const StorageKeyAgentWidth = 'bpm-editor.agentWidth';

const PaletteWidthDefault = 260;
const PaletteWidthMin = 180;
const PaletteWidthMax = 400;
const PropertiesWidthDefault = 420;
const PropertiesWidthMin = 280;
const PropertiesWidthMax = 640;
const AgentWidthDefault = 400;
const AgentWidthMin = 280;
const AgentWidthMax = 520;

type ResizeSide = 'palette' | 'properties' | 'agent';

/** 整图 import（AI / 文件）可撤销快照：undo 时若 savedToServer 则写回服务器 */
interface XmlHistoryEntry {
  xml: string;
  savedToServer: boolean;
}

const XmlHistoryMax = 20;

@Component({
  selector: 'bpm-editor',
  templateUrl: './bpm-editor.html',
  styleUrl: './bpm-editor.scss',
  providers: [{ provide: BpmEditorToken, useExisting: BpmEditor }, BpmEditorAppendService, BpmEditorReplaceService],
  imports: [
    BpmPropertiesPanel,
    BpmPallete,
    BpmToolbar,
    BpmEditorProcessMetaComponent,
    BpmDesignerAgentComponent,
    NzButtonModule,
    NzIconModule,
    NzTooltipModule
  ],
  standalone: true
})
export class BpmEditor extends BpmEditorToken implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly processDefinitionService = inject(ProcessDesignService);
  private readonly componentProvider = inject(ComponentProvider);
  private readonly elementModel = inject(ElementModel);
  private readonly append = inject(BpmEditorAppendService);
  private readonly replace = inject(BpmEditorReplaceService);
  private readonly componentService = inject(ComponentService);
  private readonly message = inject(NzMessageService);
  private readonly designerContext = inject(BpmDesignerContextService);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild(BpmToolbar) private toolbar?: BpmToolbar;

  readonly getToolbarContextFn = (): BpmDesignerToolbarContext | undefined => this.toolbar;

  recentComponentUsages = signal<ComponentDescription[]>([]);

  bpmnId = signal<string>('');
  bpmProcess = signal<BpmProcess | null>(null);
  processLoading = signal(true);

  paletteCollapsed = signal(false);
  propertiesCollapsed = signal(false);
  agentCollapsed = signal(true);
  paletteWidth = signal(PaletteWidthDefault);
  propertiesWidth = signal(PropertiesWidthDefault);
  agentWidth = signal(AgentWidthDefault);

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

  stackIdx: number | undefined = undefined;
  commandStack: any;

  private xmlUndoStack: XmlHistoryEntry[] = [];
  private xmlRedoStack: XmlHistoryEntry[] = [];
  /** 由 undo/redo 触发的 import，不再次压栈 */
  private restoringFromHistory = false;

  private resizeSide: ResizeSide | null = null;
  private resizeStartX = 0;
  private resizeStartWidth = 0;
  private narrowPropsMq?: MediaQueryList;
  private narrowPaletteMq?: MediaQueryList;
  private readonly onNarrowPropsChange = (e: MediaQueryListEvent | MediaQueryList) => this.applyNarrowProps(e.matches);
  private readonly onNarrowPaletteChange = (e: MediaQueryListEvent | MediaQueryList) => this.applyNarrowPalette(e.matches);
  private readonly onPointerMove = (e: PointerEvent) => this.onResizeMove(e);
  private readonly onPointerUp = (e: PointerEvent) => this.onResizeEnd(e);

  constructor() {
    super();
    this.restoreLayoutPrefs();
    this.route.params.pipe(takeUntilDestroyed()).subscribe(params => {
      const id = String(params['id'] ?? '');
      const prev = this.bpmnId();
      this.bpmnId.set(id);
      if (this.bpmnModeler && id && id !== prev) {
        this.loadDefinition();
      }
    });
    this.destroyRef.onDestroy(() => this.teardownNarrowMedia());
  }

  ngOnInit(): void {
    this.bpmnModeler = new BpmnModeler({
      container: '.canvas',
      additionalModules: [gridModule, customContextPadModule, appendComponentModule, replaceComponentModule, { http: ['value', this.http] }],
      kiwiAppendComponent: {
        getComponentGroups: () => this.componentProvider.componentGroups(),
        getRecentUsages: () => this.recentComponentUsages(),
        append: (sourceElement: Element, component: ComponentDescription, event: MouseEvent | undefined) => {
          this.append.appendComponentFromContextPad(sourceElement, component, event);
        }
      },
      kiwiReplaceComponent: {
        getComponentGroups: () => this.componentProvider.componentGroups(),
        getRecentUsages: () => this.recentComponentUsages(),
        getCurrentComponentId: (element: Element) => this.componentService.getComponentForElement(element)?.id,
        replace: (element: Element, component: ComponentDescription) => {
          this.replace.replaceComponentFromContextPad(element, component);
        }
      },
      moddleExtensions: {
        moddleProvider: this.elementModel.getModdleExtension(),
        componentProvider: kiwiDescriptor
      }
    });
    this.append.init(this.bpmnModeler);
    this.replace.init(this.bpmnModeler);
    this.commandStack = this.bpmnModeler.get('commandStack');
    this.registerXmlAwareUndoRedo();

    this.loadDefinition();
    this.setupNarrowMedia();

    this.processDefinitionService.getRecentComponentUsages().subscribe({
      next: list =>
        this.recentComponentUsages.set(
          (list ?? []).map(c => ({
            ...c,
            icon: c.icon || 'bpmn-icon-service-task'
          }))
        ),
      error: () => this.recentComponentUsages.set([])
    });
  }

  togglePalette(): void {
    this.paletteCollapsed.update(v => !v);
    this.persistBool(StorageKeyPaletteCollapsed, this.paletteCollapsed());
    this.notifyCanvasResized();
  }

  toggleProperties(): void {
    this.propertiesCollapsed.update(v => !v);
    this.persistBool(StorageKeyPropertiesCollapsed, this.propertiesCollapsed());
    this.notifyCanvasResized();
  }

  toggleAgent(): void {
    this.agentCollapsed.update(v => !v);
    this.persistBool(StorageKeyAgentCollapsed, this.agentCollapsed());
    this.notifyCanvasResized();
  }

  onResizeStart(event: PointerEvent, side: ResizeSide): void {
    if (event.button !== 0) {
      return;
    }
    event.preventDefault();
    this.resizeSide = side;
    this.resizeStartX = event.clientX;
    this.resizeStartWidth =
      side === 'palette' ? this.paletteWidth() : side === 'properties' ? this.propertiesWidth() : this.agentWidth();
    (event.target as HTMLElement).setPointerCapture?.(event.pointerId);
    window.addEventListener('pointermove', this.onPointerMove);
    window.addEventListener('pointerup', this.onPointerUp);
    window.addEventListener('pointercancel', this.onPointerUp);
  }

  dirty(): boolean {
    return this.commandStack._stackIdx !== this.stackIdx;
  }

  save(): Promise<any> {
    const stackIdx = this.commandStack._stackIdx;
    if (!this.dirty()) {
      return Promise.resolve(this.bpmProcess());
    }
    return new Promise(resolve => {
      this.bpmnModeler.saveXML({ format: true }).then((bpmn: any) => {
        const sentXml = bpmn.xml as string;
        this.processDefinitionService.updateProcess(this.bpmProcess()!.id!, { bpmnXml: sentXml }).subscribe((data: BpmProcess) => {
          void this.applySavedProcess(data, stackIdx, sentXml).then(saved => {
            this.refreshRecentComponentUsages();
            resolve(saved);
          });
        });
      });
    });
  }

  clearSelection(): void {
    const selection: any = this.bpmnModeler.get('selection');
    selection.select(null);
  }

  importBpmnXml(xml: string): Promise<void> {
    const trimmed = typeof xml === 'string' ? xml.trim() : '';
    return this.pushUndoSnapshot(false).then(() =>
      importBpmnXmlToModeler(this.bpmnModeler, trimmed, this.message, () => this.clearSelection())
        .then(() => {
          this.syncLocalBpmnXml(trimmed);
        })
        .catch((err: unknown) => {
          this.xmlUndoStack.pop();
          return Promise.reject(err);
        })
    );
  }

  importBpmnXmlAndSave(xml: string): Promise<void> {
    const trimmed = typeof xml === 'string' ? xml.trim() : '';
    const processId = this.bpmProcess()?.id;
    if (!processId) {
      return Promise.reject(new Error('流程未加载，无法保存'));
    }
    return this.pushUndoSnapshot(true).then(() =>
      importBpmnXmlToModeler(this.bpmnModeler, trimmed, this.message, () => this.clearSelection(), {
        notifySuccess: false
      })
        .catch((err: unknown) => {
          this.xmlUndoStack.pop();
          return Promise.reject(err);
        })
        .then(() => this.bpmnModeler.saveXML({ format: true }))
        .then(
          (bpmn: { xml?: string }) =>
            new Promise<void>((resolve, reject) => {
              this.processDefinitionService.updateProcess(processId, { bpmnXml: bpmn.xml }).subscribe({
                next: (data: BpmProcess) => {
                  void this.applySavedProcess(data, this.commandStack._stackIdx, bpmn.xml).then(() => {
                    this.refreshRecentComponentUsages();
                    this.notifyCanvasResized();
                    resolve();
                  });
                },
                error: (err: unknown) => reject(err)
              });
            })
        )
    );
  }

  undo(): void {
    void this.runUndo();
  }

  redo(): void {
    void this.runRedo();
  }

  private registerXmlAwareUndoRedo(): void {
    const editorActions = this.bpmnModeler.get('editorActions') as {
      isRegistered: (action: string) => boolean;
      unregister: (action: string) => void;
      register: (action: string, listener: () => void) => void;
    };
    // diagram-js 禁止重复 register；先卸掉默认 undo/redo 再挂我们的实现（含 XML 整图历史）
    for (const action of ['undo', 'redo'] as const) {
      if (editorActions.isRegistered(action)) {
        editorActions.unregister(action);
      }
    }
    editorActions.register('undo', () => this.undo());
    editorActions.register('redo', () => this.redo());
    this.bpmnModeler.on('commandStack.executed', () => {
      if (this.restoringFromHistory) {
        return;
      }
      this.xmlRedoStack = [];
    });
  }

  private clearXmlHistory(): void {
    this.xmlUndoStack = [];
    this.xmlRedoStack = [];
  }

  private async captureCurrentXml(): Promise<string> {
    try {
      const result = await this.bpmnModeler.saveXML({ format: false });
      return (result.xml ?? '').trim();
    } catch {
      return (this.bpmProcess()?.bpmnXml ?? '').trim();
    }
  }

  private async pushUndoSnapshot(savedToServer: boolean): Promise<void> {
    if (this.restoringFromHistory) {
      return;
    }
    const xml = await this.captureCurrentXml();
    if (!xml) {
      return;
    }
    this.xmlUndoStack.push({ xml, savedToServer });
    if (this.xmlUndoStack.length > XmlHistoryMax) {
      this.xmlUndoStack.splice(0, this.xmlUndoStack.length - XmlHistoryMax);
    }
    this.xmlRedoStack = [];
  }

  private async runUndo(): Promise<void> {
    if (this.commandStack?.canUndo?.()) {
      this.commandStack.undo();
      return;
    }
    const entry = this.xmlUndoStack.pop();
    if (!entry) {
      return;
    }
    const currentXml = await this.captureCurrentXml();
    if (currentXml) {
      this.xmlRedoStack.push({ xml: currentXml, savedToServer: entry.savedToServer });
      if (this.xmlRedoStack.length > XmlHistoryMax) {
        this.xmlRedoStack.splice(0, this.xmlRedoStack.length - XmlHistoryMax);
      }
    }
    await this.restoreXmlFromHistory(entry.xml, entry.savedToServer);
  }

  private async runRedo(): Promise<void> {
    if (this.commandStack?.canRedo?.()) {
      this.commandStack.redo();
      return;
    }
    const entry = this.xmlRedoStack.pop();
    if (!entry) {
      return;
    }
    const currentXml = await this.captureCurrentXml();
    if (currentXml) {
      this.xmlUndoStack.push({ xml: currentXml, savedToServer: entry.savedToServer });
      if (this.xmlUndoStack.length > XmlHistoryMax) {
        this.xmlUndoStack.splice(0, this.xmlUndoStack.length - XmlHistoryMax);
      }
    }
    await this.restoreXmlFromHistory(entry.xml, entry.savedToServer);
  }

  private async restoreXmlFromHistory(xml: string, saveToServer: boolean): Promise<void> {
    this.restoringFromHistory = true;
    try {
      if (saveToServer) {
        const processId = this.bpmProcess()?.id;
        if (!processId) {
          await importBpmnXmlToModeler(this.bpmnModeler, xml, this.message, () => this.clearSelection(), {
            notifySuccess: false
          });
          this.syncLocalBpmnXml(xml);
          return;
        }
        await importBpmnXmlToModeler(this.bpmnModeler, xml, this.message, () => this.clearSelection(), {
          notifySuccess: false
        });
        const bpmn = await this.bpmnModeler.saveXML({ format: true });
        await new Promise<void>((resolve, reject) => {
          this.processDefinitionService.updateProcess(processId, { bpmnXml: bpmn.xml }).subscribe({
            next: (data: BpmProcess) => {
              void this.applySavedProcess(data, this.commandStack._stackIdx, bpmn.xml).then(() => {
                this.refreshRecentComponentUsages();
                this.notifyCanvasResized();
                resolve();
              });
            },
            error: (err: unknown) => reject(err)
          });
        });
      } else {
        await importBpmnXmlToModeler(this.bpmnModeler, xml, this.message, () => this.clearSelection(), {
          notifySuccess: false
        });
        this.syncLocalBpmnXml(xml);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '恢复流程失败';
      this.message.error(msg);
    } finally {
      this.restoringFromHistory = false;
    }
  }

  private syncLocalBpmnXml(trimmed: string): void {
    const process = this.bpmProcess();
    if (process) {
      this.bpmProcess.set({ ...process, bpmnXml: trimmed });
    }
    this.notifyCanvasResized();
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
      return new Promise(resolve => {
        const beforeXml = (this.bpmProcess()?.bpmnXml ?? '').trim();
        this.processDefinitionService.deployProcess(this.bpmProcess()!.id!).subscribe((data: BpmProcess) => {
          void this.applySavedProcess(data, this.commandStack._stackIdx, beforeXml).then(saved => {
            resolve(saved);
          });
        });
      });
    });
  }

  loadDefinition(): void {
    this.clearXmlHistory();
    this.processLoading.set(true);
    this.processDefinitionService
      .getProcessById(this.bpmnId())
      .pipe(finalize(() => this.processLoading.set(false)))
      .subscribe({
        next: (data: BpmProcess) => {
          this.bpmProcess.set(data);
          this.designerContext.setProjectId(data.projectId ?? null);
          this.bpmnModeler.importXML(data.bpmnXml ?? '');
        },
        error: () => {
          this.bpmProcess.set(null);
          this.designerContext.setProjectId(null);
        }
      });
  }

  private applySavedProcess(data: BpmProcess, stackIdx?: number, sentXml?: string): Promise<BpmProcess> {
    this.bpmProcess.set(data);
    if (stackIdx !== undefined) {
      this.stackIdx = stackIdx;
    }
    const serverXml = (data.bpmnXml ?? '').trim();
    const normalizedSent = (sentXml ?? '').trim();
    if (!serverXml || !normalizedSent || serverXml === normalizedSent) {
      return Promise.resolve(data);
    }
    return importBpmnXmlToModeler(this.bpmnModeler, serverXml, this.message, () => this.clearSelection(), {
      notifySuccess: false
    }).then(() => {
      this.syncLocalBpmnXml(serverXml);
      return data;
    });
  }

  private refreshRecentComponentUsages(): void {
    this.processDefinitionService.getRecentComponentUsages().subscribe({
      next: list =>
        this.recentComponentUsages.set(
          (list ?? []).map(c => ({
            ...c,
            icon: c.icon || 'bpmn-icon-service-task'
          }))
        ),
      error: () => {}
    });
  }

  private restoreLayoutPrefs(): void {
    this.paletteCollapsed.set(this.readBool(StorageKeyPaletteCollapsed, false));
    this.propertiesCollapsed.set(this.readBool(StorageKeyPropertiesCollapsed, false));
    this.agentCollapsed.set(this.readBool(StorageKeyAgentCollapsed, true));
    this.paletteWidth.set(this.readClampedWidth(StorageKeyPaletteWidth, PaletteWidthDefault, PaletteWidthMin, PaletteWidthMax));
    this.propertiesWidth.set(
      this.readClampedWidth(StorageKeyPropertiesWidth, PropertiesWidthDefault, PropertiesWidthMin, PropertiesWidthMax)
    );
    this.agentWidth.set(this.readClampedWidth(StorageKeyAgentWidth, AgentWidthDefault, AgentWidthMin, AgentWidthMax));
  }

  private setupNarrowMedia(): void {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return;
    }
    this.narrowPropsMq = window.matchMedia('(max-width: 1200px)');
    this.narrowPaletteMq = window.matchMedia('(max-width: 900px)');
    this.applyNarrowProps(this.narrowPropsMq.matches);
    this.applyNarrowPalette(this.narrowPaletteMq.matches);
    this.narrowPropsMq.addEventListener('change', this.onNarrowPropsChange);
    this.narrowPaletteMq.addEventListener('change', this.onNarrowPaletteChange);
  }

  private teardownNarrowMedia(): void {
    this.narrowPropsMq?.removeEventListener('change', this.onNarrowPropsChange);
    this.narrowPaletteMq?.removeEventListener('change', this.onNarrowPaletteChange);
    this.narrowPropsMq = undefined;
    this.narrowPaletteMq = undefined;
    window.removeEventListener('pointermove', this.onPointerMove);
    window.removeEventListener('pointerup', this.onPointerUp);
    window.removeEventListener('pointercancel', this.onPointerUp);
  }

  private applyNarrowProps(matches: boolean): void {
    if (matches) {
      let changed = false;
      if (!this.propertiesCollapsed()) {
        this.propertiesCollapsed.set(true);
        changed = true;
      }
      if (!this.agentCollapsed()) {
        this.agentCollapsed.set(true);
        changed = true;
      }
      if (changed) {
        this.notifyCanvasResized();
      }
      return;
    }
    const preferredProps = this.readBool(StorageKeyPropertiesCollapsed, false);
    const preferredAgent = this.readBool(StorageKeyAgentCollapsed, true);
    let changed = false;
    if (this.propertiesCollapsed() !== preferredProps) {
      this.propertiesCollapsed.set(preferredProps);
      changed = true;
    }
    if (this.agentCollapsed() !== preferredAgent) {
      this.agentCollapsed.set(preferredAgent);
      changed = true;
    }
    if (changed) {
      this.notifyCanvasResized();
    }
  }

  private applyNarrowPalette(matches: boolean): void {
    if (matches) {
      if (!this.paletteCollapsed()) {
        this.paletteCollapsed.set(true);
        this.notifyCanvasResized();
      }
      return;
    }
    const preferred = this.readBool(StorageKeyPaletteCollapsed, false);
    if (this.paletteCollapsed() !== preferred) {
      this.paletteCollapsed.set(preferred);
      this.notifyCanvasResized();
    }
  }

  private onResizeMove(event: PointerEvent): void {
    if (!this.resizeSide) {
      return;
    }
    const delta = event.clientX - this.resizeStartX;
    if (this.resizeSide === 'palette') {
      this.paletteWidth.set(this.clamp(this.resizeStartWidth + delta, PaletteWidthMin, PaletteWidthMax));
    } else if (this.resizeSide === 'properties') {
      this.propertiesWidth.set(this.clamp(this.resizeStartWidth - delta, PropertiesWidthMin, PropertiesWidthMax));
    } else {
      this.agentWidth.set(this.clamp(this.resizeStartWidth - delta, AgentWidthMin, AgentWidthMax));
    }
    this.notifyCanvasResized();
  }

  private onResizeEnd(event: PointerEvent): void {
    if (!this.resizeSide) {
      return;
    }
    const side = this.resizeSide;
    this.resizeSide = null;
    try {
      (event.target as HTMLElement).releasePointerCapture?.(event.pointerId);
    } catch {
      /* ignore */
    }
    window.removeEventListener('pointermove', this.onPointerMove);
    window.removeEventListener('pointerup', this.onPointerUp);
    window.removeEventListener('pointercancel', this.onPointerUp);
    if (side === 'palette') {
      this.persistNumber(StorageKeyPaletteWidth, this.paletteWidth());
    } else if (side === 'properties') {
      this.persistNumber(StorageKeyPropertiesWidth, this.propertiesWidth());
    } else {
      this.persistNumber(StorageKeyAgentWidth, this.agentWidth());
    }
    this.notifyCanvasResized();
  }

  private notifyCanvasResized(): void {
    if (!this.bpmnModeler) {
      return;
    }
    const canvas = this.bpmnModeler.get('canvas') as { resized?: () => void };
    canvas.resized?.();
  }

  private readBool(key: string, fallback: boolean): boolean {
    try {
      const raw = localStorage.getItem(key);
      if (raw === null) {
        return fallback;
      }
      return raw === '1' || raw === 'true';
    } catch {
      return fallback;
    }
  }

  private persistBool(key: string, value: boolean): void {
    try {
      localStorage.setItem(key, value ? '1' : '0');
    } catch {
      /* ignore */
    }
  }

  private readClampedWidth(key: string, fallback: number, min: number, max: number): number {
    try {
      const raw = localStorage.getItem(key);
      if (raw == null) {
        return fallback;
      }
      const n = Number(raw);
      if (!Number.isFinite(n)) {
        return fallback;
      }
      return this.clamp(n, min, max);
    } catch {
      return fallback;
    }
  }

  private persistNumber(key: string, value: number): void {
    try {
      localStorage.setItem(key, String(value));
    } catch {
      /* ignore */
    }
  }

  private clamp(n: number, min: number, max: number): number {
    return Math.min(max, Math.max(min, Math.round(n)));
  }
}
