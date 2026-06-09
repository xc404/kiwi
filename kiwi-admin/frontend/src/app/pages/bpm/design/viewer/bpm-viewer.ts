import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  effect,
  inject,
  signal
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/diagram-js.css';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import { NzLayoutComponent, NzLayoutModule } from 'ng-zorro-antd/layout';
import { NzMessageService } from 'ng-zorro-antd/message';
import { interval } from 'rxjs';
import { filter, map as mapOp } from 'rxjs/operators';
import {
  BpmActivityVisualState,
  BpmInstanceRecoverResultDto,
  BpmProcessInstanceDto,
  CamundaHistoricActivityInstance,
  ProcessInstanceService
} from '../service/process-instance.service';
import { BpmPropertiesPanel } from "../property-panel/properties-panel";
import { BPM_ACTIVITY_MARKER_NAMES, BpmActivityMarkerName } from './bpm-activity-markers';
import { BpmViewerHeaderComponent } from './bpm-viewer-header.component';
import {
  buildCalledProcessInstanceMap,
  clearCallActivityLinkOverlays,
  syncCallActivityLinkOverlays,
} from './bpm-viewer-call-activity-links';
import { ElementModel } from '../extension/element-model';
import kiwiDescriptor from '../../flow-elements/kiwi.json';
@Component({
  selector: 'bpm-viewer',
  templateUrl: './bpm-viewer.html',
  styleUrl: './bpm-viewer.scss',
  imports: [NzLayoutComponent, NzLayoutModule, BpmPropertiesPanel, BpmViewerHeaderComponent],
  standalone: true,
})
export class BpmViewer implements OnInit, OnDestroy {
  /** 运行中实例轮询间隔（毫秒） */
  private static readonly AUTO_REFRESH_INTERVAL_MS = 3_000;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly processInstanceService = inject(ProcessInstanceService);
  private readonly message = inject(NzMessageService);

  @ViewChild('canvasHost', { static: true }) canvasHost!: ElementRef<HTMLElement>;

  protected readonly processInstance = signal<BpmProcessInstanceDto>(undefined as unknown as BpmProcessInstanceDto);

  protected viewer!: NavigatedViewer;
  protected canvas!: any;
  protected elementRegistry!: any;
  elementModel = inject(ElementModel);
  processInstanceId = signal<string | undefined>(undefined);
  processActivities = signal<CamundaHistoricActivityInstance[]>([]);
  activityStates = signal<Map<string, CamundaHistoricActivityInstance>>(new Map());
  /** CallActivity activityId -> 子流程实例 ID */
  calledProcessInstanceMap = signal<Map<string, string>>(new Map());
  bpmnXml = signal<string | undefined>(undefined);

  /** 一键恢复请求中（避免重复点击；用于按钮 loading 态） */
  protected readonly recovering = signal(false);


  /** 最近一次 importXML 已成功，可与 processActivities 叠加打标 */
  private readonly bpmnImportReady = signal(false);

  /** 已加载 BPMN 的流程定义 id，避免轮询刷新实例时重复拉取 XML */
  private readonly loadedProcessDefinitionId = signal<string | undefined>(undefined);

  private readonly activityMarkerNames = BPM_ACTIVITY_MARKER_NAMES;
  private markedActivityElementIds: string[] = [];

  /** diagram-js 默认使用 marker `selected`；在 SelectionVisuals 之后同步为 `kiwi-bpmn-selected`（EventBus 默认优先级 1000，此处用更低优先级以在其后执行） */



  constructor() {
    effect(() => {
      let processInstanceId = this.processInstanceId();
      if (processInstanceId) {
        this.loadProcessInstance();
        this.loadProcessActivities();
      }
    });

    effect(() => {
      const defId = this.processInstance()?.processDefinitionId?.trim();
      if (defId && defId !== this.loadedProcessDefinitionId()) {
        this.loadedProcessDefinitionId.set(defId);
        this.loadBpmnXml();
      }
    });

    effect((onCleanup) => {
      if (!this.isProcessInstanceRunning(this.processInstance())) {
        return;
      }
      const sub = interval(BpmViewer.AUTO_REFRESH_INTERVAL_MS).subscribe(() => {
        this.refreshRuntimeData();
      });
      onCleanup(() => sub.unsubscribe());
    });

    effect(() => {
      let bpmnXml = this.bpmnXml();
      if (bpmnXml) {
        this.loadXml(bpmnXml);
      }
    });

    effect(() => {
      this.activityStates();
      this.processActivities();
      this.bpmnImportReady();
      this.processInstance();
      this.markActivityState();
    });

    effect(() => {
      this.bpmnImportReady();
      this.calledProcessInstanceMap();
      this.syncSubprocessLinkOverlays();
    });
  }

  ngOnInit(): void {
    this.viewer = new NavigatedViewer({
      container: this.canvasHost.nativeElement,
      moddleExtensions: {
        moddleProvider: this.elementModel.getModdleExtension(),
        componentProvider: kiwiDescriptor
      }
    });
    this.canvas = this.viewer.get('canvas');
    this.elementRegistry = this.viewer.get('elementRegistry');
    this.route.paramMap
      .pipe(
        mapOp((p) => p.get('processInstanceId')),
        filter((id): id is string => !!id),
      )
      .subscribe((id) => {
        this.loadedProcessDefinitionId.set(undefined);
        this.processInstanceId.set(id);
      });

    const pendingXml = this.bpmnXml();
    if (pendingXml) {
      void this.loadXml(pendingXml);
    }

    this.viewer.on('selection.changed', 100, this.onSelectionChanged, this);
  }

  ngOnDestroy(): void {
    if (this.viewer) {
      clearCallActivityLinkOverlays(this.viewer);
      this.viewer.off('selection.changed', this.onSelectionChanged);
      this.viewer.destroy();
    }
  }



  loadProcessInstance() {
    this.processInstanceService.getProcessInstance(this.processInstanceId()!).subscribe({
      next: (pi) => {
        this.processInstance.set(pi);
      },
      error: (err) => {
        console.error('加载流程实例失败', err);
      },
    });
  }

  loadBpmnXml() {
    const instanceId = this.processInstanceId();
    if (!instanceId) {
      console.error('无法解析流程实例 ID');
      return;
    }
    this.processInstanceService.getProcessDefinitionXml(instanceId).subscribe({
      next: (res) => {
        this.bpmnXml.set(res.bpmn20Xml);
      },
      error: (err) => {
        console.error('获取流程定义 XML 失败', err);
      },
    });
  }

  loadProcessActivities() {
    this.processInstanceService.getHistoryActivityInstances(this.processInstanceId()!).subscribe({
      next: (activities) => {
        this.processActivities.set(activities);
        this.activityStates.set(this.buildActivityStateMap(activities));
        this.calledProcessInstanceMap.set(buildCalledProcessInstanceMap(activities));
      }
    });
  }

  private syncSubprocessLinkOverlays(): void {
    if (!this.bpmnImportReady() || !this.viewer) {
      if (this.viewer) {
        clearCallActivityLinkOverlays(this.viewer);
      }
      return;
    }
    syncCallActivityLinkOverlays(
      this.viewer,
      this.calledProcessInstanceMap(),
      (childId) => this.openChildProcessInstanceViewer(childId),
    );
  }

  private openChildProcessInstanceViewer(childProcessInstanceId: string): void {
    const id = childProcessInstanceId.trim();
    if (!id) {
      return;
    }
    const url = new URL(window.location.href);
    url.hash = this.router.serializeUrl(
      this.router.createUrlTree(['/bpm/process-instance', id]),
    );
    window.open(url.toString(), '_blank', 'noopener,noreferrer');
  }

  /** 轮询时仅刷新实例状态与活动历史，不重复加载 BPMN 定义 */
  private refreshRuntimeData(): void {
    if (!this.processInstanceId()) {
      return;
    }
    this.loadProcessInstance();
    this.loadProcessActivities();
  }

  /**
   * 一键恢复：调用 POST /bpm/process-instance/{id}/recover，重置 OPEN incident 关联的
   * Job / External Task retries；成功后立即拉取一次最新状态以反馈结果。
   */
  protected onRecoverRequested(): void {
    const id = this.processInstanceId();
    if (!id || this.recovering()) {
      return;
    }
    this.recovering.set(true);
    this.processInstanceService.recoverProcessInstance(id).subscribe({
      next: (res) => {
        this.recovering.set(false);
        this.message.success(this.formatRecoverResult(res));
        this.refreshRuntimeData();
      },
      error: (err) => {
        this.recovering.set(false);
        console.error('一键恢复失败', err);
        this.message.error('一键恢复失败，请稍后重试');
      },
    });
  }

  private formatRecoverResult(res: BpmInstanceRecoverResultDto): string {
    return (
      `已恢复：Job ${res.jobsRetried} 个、External Task ${res.externalTasksRetried} 个` +
      `（OPEN incident ${res.openIncidentCount}，跳过 ${res.incidentsSkipped}，retries=${res.retriesApplied}）`
    );
  }

  /** 与工具栏状态「运行中」一致：未结束、未挂起、无 open incident 且非 ERROR。 */
  private isProcessInstanceRunning(pi: BpmProcessInstanceDto | undefined): boolean {
    if (!pi?.id) {
      return false;
    }
    const state = String(pi.state ?? '')
      .trim()
      .toUpperCase();
    const hasOpenIncidents = (pi.openIncidents?.length ?? 0) > 0;
    if (state === 'ERROR' || hasOpenIncidents) {
      return false;
    }
    if (pi.suspended || state === 'SUSPENDED') {
      return false;
    }
    if (pi.ended || state === 'COMPLETED' || state === 'CANCELED') {
      return false;
    }
    return true;
  }

  /**
   * 同一 BPMN activityId 可能有多条历史实例（重试/多实例）：优先保留未结束的一条，
   * 以便与 open incident 对齐；同态时保留 startTime 更晚的。
   */
  private buildActivityStateMap(
    activities: CamundaHistoricActivityInstance[],
  ): Map<string, CamundaHistoricActivityInstance> {
    const activityMap = new Map<string, CamundaHistoricActivityInstance>();
    for (const activity of activities) {
      const activityId = activity.activityId?.trim();
      if (!activityId) {
        continue;
      }
      const existing = activityMap.get(activityId);
      if (!existing) {
        activityMap.set(activityId, activity);
        continue;
      }
      if (existing.completed && !activity.completed) {
        activityMap.set(activityId, activity);
        continue;
      }
      if (!existing.completed && activity.completed) {
        continue;
      }
      const existingStart = this.historicActivityStartMs(existing);
      const nextStart = this.historicActivityStartMs(activity);
      if (nextStart >= existingStart) {
        activityMap.set(activityId, activity);
      }
    }
    return activityMap;
  }

  private historicActivityStartMs(activity: CamundaHistoricActivityInstance): number {
    const raw = activity.startTime;
    if (raw == null || raw === '') {
      return 0;
    }
    const t = Date.parse(String(raw));
    return Number.isNaN(t) ? 0 : t;
  }

  /**
   * 无 BPMNDI（仅有语义）时 bpmn-js 会抛出 “no diagram to display”。
   * 使用 bpmn-auto-layout 补全图形后再导入。
   */
  private loadXml(bpmn20Xml: string | undefined): void {
    const trimmedXml = typeof bpmn20Xml === 'string' ? bpmn20Xml.trim() : '';
    if (!trimmedXml) {
      console.error('流程定义 XML 为空');
      return;
    }

    const viewer = this.viewer;
    if (!viewer) {
      return;
    }

    this.bpmnImportReady.set(false);
    void viewer
      .importXML(trimmedXml)
      .then(() => {
        this.bpmnImportReady.set(true);
      })
      .catch((err: unknown) => {
        console.error('导入 BPMN XML 失败', err);
        this.bpmnImportReady.set(false);
      });
  }



  private clearActivityMarkers(): void {
    for (const id of this.markedActivityElementIds) {
      for (const name of this.activityMarkerNames) {
        this.removeActivityMarker(id, name);
      }
    }
    this.markedActivityElementIds = [];
  }

  private markActivityState(): void {
    if (!this.bpmnImportReady()) {
      this.clearActivityMarkers();
      return;
    }

    this.clearActivityMarkers();

    const errorActivityIds = this.errorActivityIds();

    for (const activityId of errorActivityIds) {
      this.applyActivityMarker(activityId, 'kiwi-bpmn-error');
    }

    for (const activity of this.activityStates().values()) {
      const activityId = activity.activityId?.trim();
      if (!activityId || errorActivityIds.has(activityId)) {
        continue;
      }
      const marker = this.markerForHistoricActivity(activity, errorActivityIds);
      if (!marker) {
        continue;
      }
      this.applyActivityMarker(activityId, marker);
    }
  }

  private applyActivityMarker(activityId: string, marker: BpmActivityMarkerName): void {
    if (this.addActivityMarker(activityId, marker)) {
      this.markedActivityElementIds.push(activityId);
    }
  }

  /**
   * 异常节点 id：open incident、历史活动上的 incidentIds，以及 ERROR 态下的 currentActivities。
   */
  private errorActivityIds(): Set<string> {
    const set = new Set<string>();

    const incidents = this.processInstance()?.openIncidents;
    if (incidents?.length) {
      for (const inc of incidents) {
        const aid = inc.activityId?.trim();
        if (aid) {
          set.add(aid);
        }
      }
    }

    for (const activity of this.processActivities()) {
      const aid = activity.activityId?.trim();
      if (!aid) {
        continue;
      }
      if (activity.incidentIds?.length) {
        set.add(aid);
      }
    }

    const state = String(this.processInstance()?.state ?? '')
      .trim()
      .toUpperCase();
    if (state === 'ERROR') {
      const currents = this.processInstance()?.currentActivities as
        | Array<{ activityId?: string | null }>
        | undefined;
      for (const cur of currents ?? []) {
        const aid = cur.activityId?.trim();
        if (aid) {
          set.add(aid);
        }
      }
    }

    return set;
  }

  /**
   * 与 activityStates 一致的四种语义：已结束 / error / 运行中；（未运行不入 Map，无 marker）。
   */
  private markerForHistoricActivity(
    activity: CamundaHistoricActivityInstance,
    errorActivityIds: Set<string>,
  ): BpmActivityMarkerName | null {
    const id = activity.activityId?.trim();
    if (!id) {
      return null;
    }
    if (errorActivityIds.has(id) || (activity.incidentIds?.length ?? 0) > 0) {
      return 'kiwi-bpmn-error';
    }
    if (!activity.completed) {
      return 'kiwi-bpmn-active';
    }
    if (activity.canceled) {
      return 'kiwi-bpmn-error';
    }
    return 'kiwi-bpmn-completed';
  }

  getActivityVisualState(activityId: string | undefined | null): BpmActivityVisualState {
    const id = activityId?.trim();
    if (!id) {
      return 'notStarted';
    }
    const activity = this.activityStates().get(id);
    if (!activity) {
      return 'notStarted';
    }
    const errorIds = this.errorActivityIds();
    if (errorIds.has(id)) {
      return 'error';
    }
    if ((activity.incidentIds?.length ?? 0) > 0) {
      return 'error';
    }
    if (!activity.completed) {
      return 'running';
    }
    if (activity.canceled) {
      return 'error';
    }
    return 'completed';
  }


  onSelectionChanged(event: {
    oldSelection: unknown[];
    newSelection: unknown[];
  }): void {
    if (!this.canvas) {
      return;
    }
    const oldSel = event.oldSelection ?? [];
    const newSel = event.newSelection ?? [];
    for (const el of oldSel) {
      if (newSel.indexOf(el) === -1) {
        this.canvas.removeMarker(el, 'kiwi-bpmn-selected');
      }
    }
    for (const el of newSel) {
      if (oldSel.indexOf(el) === -1) {
        this.canvas.removeMarker(el, 'selected');
        this.canvas.addMarker(el, 'kiwi-bpmn-selected');
      }
    }
  };

  removeActivityMarker(activityId: string, marker: string) {
    if (!this.canvas) {
      return;
    }
    const element = this.resolveDiagramElement(activityId);
    if (!element) {
      return;
    }
    this.canvas.removeMarker(element, marker);
  }

  addActivityMarker(activityId: string, marker: string): boolean {
    if (!this.elementRegistry || !this.canvas) {
      return false;
    }
    const element = this.resolveDiagramElement(activityId);
    if (!element) {
      return false;
    }
    this.canvas.addMarker(element, marker);
    return true;
  }

  /** 按 BPMN 元素 id 解析画布节点（兼容 registry 键与 businessObject.id 不一致） */
  private resolveDiagramElement(activityId: string): unknown | undefined {
    const id = activityId.trim();
    if (!id) {
      return undefined;
    }
    const direct = this.elementRegistry.get(id);
    if (direct) {
      return direct;
    }
    const all = this.elementRegistry.getAll() as Array<{ businessObject?: { id?: string } }>;
    return all.find((el) => el.businessObject?.id === id);
  }

  /** 是否正常结束（不含取消 / incident） */
  isActivityCompleted(activityId: string): boolean {
    return this.getActivityVisualState(activityId) === 'completed';
  }
}
