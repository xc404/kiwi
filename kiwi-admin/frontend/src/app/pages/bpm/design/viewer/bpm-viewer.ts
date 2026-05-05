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
import { ActivatedRoute } from '@angular/router';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/diagram-js.css';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import { NzLayoutComponent, NzLayoutModule } from 'ng-zorro-antd/layout';
import { filter, map as mapOp } from 'rxjs/operators';
import {
  BpmActivityVisualState,
  BpmProcessInstanceDto,
  CamundaHistoricActivityInstance,
  ProcessInstanceService
} from '../service/process-instance.service';
import { BpmPropertiesPanel } from "../property-panel/properties-panel";
import { BPM_ACTIVITY_MARKER_NAMES, BpmActivityMarkerName } from './bpm-activity-markers';
import { BpmViewerHeaderComponent } from './bpm-viewer-header.component';
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
  private readonly route = inject(ActivatedRoute);
  private readonly processInstanceService = inject(ProcessInstanceService);

  @ViewChild('canvasHost', { static: true }) canvasHost!: ElementRef<HTMLElement>;

  protected readonly processInstance = signal<BpmProcessInstanceDto>(undefined as unknown as BpmProcessInstanceDto);

  protected viewer!: NavigatedViewer;
  protected canvas!: any;
  protected elementRegistry!: any;
  elementModel = inject(ElementModel);
  processInstanceId = signal<string | undefined>(undefined);
  processActivities = signal<CamundaHistoricActivityInstance[]>([]);
  activityStates = signal<Map<string, CamundaHistoricActivityInstance>>(new Map());
  bpmnXml = signal<string | undefined>(undefined);


  /** 最近一次 importXML 已成功，可与 processActivities 叠加打标 */
  private readonly bpmnImportReady = signal(false);

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
      let processInstance = this.processInstance();
      if (processInstance) {
        this.loadBpmnXml();
      }
    });

    effect(() => {
      let bpmnXml = this.bpmnXml();
      if (bpmnXml) {
        this.loadXml(bpmnXml);
      }
    });

    effect(() => {
      this.activityStates();
      this.bpmnImportReady();
      this.processInstance();
      this.markActivityState();
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
      this.viewer.off('selection.changed', this.onSelectionChanged);
      this.viewer.destroy();
    }
  }



  loadProcessInstance() {
    this.processInstanceService.getProcessInstance(this.processInstanceId()!).subscribe({
      next: (pi) => {
        this.processInstance.set(pi);
        this.ensureProcessDefinitionName(pi);
      },
      error: (err) => {
        console.error('加载流程实例失败', err);
      },
    });
  }

  /** 运行时实例常无 processDefinitionName，补拉 GET /process-definition/{id} */
  private ensureProcessDefinitionName(pi: BpmProcessInstanceDto): void {
    const defId = pi.processDefinitionId?.trim();
    if (!defId) {
      return;
    }
    if (pi.processDefinitionName?.trim()) {
      return;
    }
    this.processInstanceService.getProcessDefinition(defId).subscribe({
      next: (def) => {
        const n = def.name != null ? String(def.name).trim() : '';
        if (!n) {
          return;
        }
        const cur = this.processInstance();
        if (cur?.id !== pi.id) {
          return;
        }
        if (cur.processDefinitionName?.trim()) {
          return;
        }
        this.processInstance.set({ ...cur, processDefinitionName: n });
      },
      error: () => {
        /* 名称非关键路径，忽略 */
      },
    });
  }
  loadBpmnXml() {
    const definitionId = this.processInstance().processDefinitionId;
    if (!definitionId) {
      console.error('无法解析流程定义 ID（definitionId / processDefinitionId）');
      return;
    }
    this.processInstanceService.getProcessDefinitionXml(definitionId).subscribe({
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
        const activityMap = new Map<string, CamundaHistoricActivityInstance>();
        for (const activity of activities) {
          if (activity.activityId == null || activity.activityId === '') {
            continue;
          }
          const activityId = activity.activityId;
          if (activity.completed) {
            activityMap.set(activityId, activity);
          }

          let exist = activityMap.get(activityId);
          if (exist?.completed) {
            continue;
          }
          activityMap.set(activityId, activity);
        }
        this.activityStates.set(activityMap);
      }
    });
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

    const incidentIds = this.openIncidentActivityIds();

    for (const activity of this.activityStates().values()) {
      const marker = this.markerForHistoricActivity(activity, incidentIds);
      if (!marker) {
        continue;
      }
      this.addActivityMarker(activity.activityId!, marker);
      this.markedActivityElementIds.push(activity.activityId!);
    }
  }

  /** 运行中实例上未关闭 incident 对应的 BPMN activityId */
  private openIncidentActivityIds(): Set<string> {
    const incidents = this.processInstance()?.openIncidents;
    const set = new Set<string>();
    if (!incidents?.length) {
      return set;
    }
    for (const inc of incidents) {
      const aid = inc.activityId?.trim();
      if (aid) {
        set.add(aid);
      }
    }
    return set;
  }

  /**
   * 与 activityStates 一致的四种语义：已结束 / error / 运行中；（未运行不入 Map，无 marker）。
   */
  private markerForHistoricActivity(
    activity: CamundaHistoricActivityInstance,
    incidentActivityIds: Set<string>,
  ): BpmActivityMarkerName | null {
    const id = activity.activityId?.trim();
    if (!id) {
      return null;
    }
    if (incidentActivityIds.has(id)) {
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
    const incidents = this.openIncidentActivityIds();
    if (incidents.has(id)) {
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
    const viewer = this.viewer;
    if (!viewer) {
      return;
    }
    this.canvas!.removeMarker(activityId, marker);
  }

  addActivityMarker(activityId: string, marker: string) {
    if (!this.elementRegistry) {
      return;
    }
    if (!this.canvas) {
      return;
    }
    let element = this.elementRegistry.get(activityId);
    if (!element) {
      return;
    }
    this.canvas!.addMarker(activityId, marker);
  }

  /** 是否正常结束（不含取消 / incident） */
  isActivityCompleted(activityId: string): boolean {
    return this.getActivityVisualState(activityId) === 'completed';
  }
}
