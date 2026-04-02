import { HttpErrorResponse } from '@angular/common/http';
import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  computed,
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
import { NzTagModule } from 'ng-zorro-antd/tag';
import { catchError } from 'rxjs';
import { filter, map as mapOp } from 'rxjs/operators';
import {
  CamundaHistoricActivityInstance,
  ProcessInstance,
  ProcessInstanceService
} from '../service/process-instance.service';

@Component({
  selector: 'bpm-viewer',
  templateUrl: './bpm-viewer.html',
  styleUrl: './bpm-viewer.scss',
  imports: [NzTagModule, NzLayoutComponent, NzLayoutModule],
  standalone: true,
})
export class BpmViewer implements AfterViewInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly processInstanceService = inject(ProcessInstanceService);

  @ViewChild('canvasHost', { static: true }) canvasHost!: ElementRef<HTMLElement>;

  protected readonly processInstance = signal<ProcessInstance>(undefined as unknown as ProcessInstance);

  protected viewer: NavigatedViewer | null = null;
  processInstanceId = signal<string | undefined>(undefined);
  processActivities = signal<CamundaHistoricActivityInstance[]>([]);
  bpmnXml = signal<string | undefined>(undefined);


  /** 最近一次 importXML 已成功，可与 processActivities 叠加打标 */
  private readonly bpmnImportReady = signal(false);

  private readonly activityMarkerNames = ['kiwi-bpmn-completed', 'kiwi-bpmn-active'] as const;
  private markedActivityElementIds: string[] = [];

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
      this.processActivities();
      this.bpmnImportReady();
      this.applyActivityMarkers();
    });
  }

  protected readonly statusLabel = computed(() => {
    const v = this.processInstance();
    if (!v) {
      return '';
    }
    if (v.suspended) {
      return '已挂起';
    }
    if (v.ended) {
      return '已结束';
    }
    return '运行中';
  });

  protected readonly statusColor = computed(() => {
    const v = this.processInstance();
    if (!v) {
      return 'default';
    }
    if (v.suspended) {
      return 'warning';
    }
    if (v.ended) {
      return 'default';
    }
    return 'processing';
  });



  ngAfterViewInit(): void {
    this.viewer = new NavigatedViewer({
      container: this.canvasHost.nativeElement,
    });

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
  }

  ngOnDestroy(): void {
    this.viewer?.destroy();
    this.viewer = null;
  }



  loadProcessInstance() {
    this.processInstanceService.getRuntimeProcessInstance(this.processInstanceId()!).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 404) {
          // 404 可能是因为流程实例已结束，尝试获取历史流程实例
          return this.processInstanceService.getHistoricProcessInstance(this.processInstanceId()!);
        }
        throw err;
      }),
    ).subscribe({
      next: (pi) => {
        this.processInstance.set(pi);
      },
      error: (err) => {
        console.error('加载流程实例失败', err);
      },
    });
  }

  loadProcessActivities() {
    this.processInstanceService.getHistoryActivityInstances(this.processInstanceId()!).subscribe({
      next: (activities) => {
        this.processActivities.set(activities);
      },
    });
  }




  loadBpmnXml() {
    const definitionId = this.processInstance().definitionId;
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

  /**
   * 同一 activityId 多次出现（重试、多实例等）：任一条 endTime 为空则视为当前活动，否则已完成。
   */
  private activityStatesFromHistory(
    activities: CamundaHistoricActivityInstance[],
  ): Map<string, 'active' | 'completed'> {
    const byActivityId = new Map<string, CamundaHistoricActivityInstance[]>();
    for (const a of activities) {
      const aid = a.activityId;
      if (aid == null || aid === '') {
        continue;
      }
      let list = byActivityId.get(aid);
      if (!list) {
        list = [];
        byActivityId.set(aid, list);
      }
      list.push(a);
    }
    const out = new Map<string, 'active' | 'completed'>();
    for (const [activityId, list] of byActivityId) {
      const hasOpen = list.some((x) => x.endTime == null || x.endTime === '');
      out.set(activityId, hasOpen ? 'active' : 'completed');
    }
    return out;
  }

  private clearActivityMarkers(): void {
    const viewer = this.viewer;
    if (!viewer) {
      return;
    }
    const canvas = viewer.get('canvas') as {
      removeMarker: (elementId: string, marker: string) => void;
    };
    for (const id of this.markedActivityElementIds) {
      for (const name of this.activityMarkerNames) {
        canvas.removeMarker(id, name);
      }
    }
    this.markedActivityElementIds = [];
  }

  private applyActivityMarkers(): void {
    const viewer = this.viewer;
    if (!viewer || !this.bpmnImportReady()) {
      return;
    }

    this.clearActivityMarkers();

    const elementRegistry = viewer.get('elementRegistry') as {
      get: (id: string) => { type?: string } | undefined;
    };
    const canvas = viewer.get('canvas') as {
      addMarker: (elementId: string, marker: string) => void;
    };

    const states = this.activityStatesFromHistory(this.processActivities());
    for (const [activityId, state] of states) {
      const el = elementRegistry.get(activityId);
      if (!el) {
        continue;
      }
      const marker = state === 'active' ? 'kiwi-bpmn-active' : 'kiwi-bpmn-completed';
      canvas.addMarker(activityId, marker);
      this.markedActivityElementIds.push(activityId);
    }
  }
}
