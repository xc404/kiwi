import { HttpErrorResponse } from '@angular/common/http';
import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/diagram-js.css';
import { layoutProcess } from 'bpmn-auto-layout';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import { NzLayoutComponent, NzLayoutModule } from 'ng-zorro-antd/layout';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { catchError, finalize } from 'rxjs';
import { filter, map as mapOp } from 'rxjs/operators';
import {
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

  protected readonly loading = signal(false);
  protected readonly processInstance = signal<ProcessInstance>(undefined as unknown as ProcessInstance);
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

  protected viewer: NavigatedViewer | null = null;
  processInstanceId!: string;

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
        this.loadDiagram(id);
      });
  }

  ngOnDestroy(): void {
    this.viewer?.destroy();
    this.viewer = null;
  }


  private loadDiagram(processInstanceId: string): void {
    this.processInstanceId = processInstanceId;
    this.loadProcessInstance();

    // this.loadBpmnXml();
  }

  loadProcessInstance() {
    this.loading.set(true);
    this.processInstanceService.getRuntimeProcessInstance(this.processInstanceId).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 404) {
          // 404 可能是因为流程实例已结束，尝试获取历史流程实例
          return this.processInstanceService.getHistoricProcessInstance(this.processInstanceId);
        }
        throw err;
      }),
      finalize(() => this.loading.set(false)),
    ).subscribe({
      next: (pi) => {
        this.processInstance.set(pi);
        this.loadBpmnXml();
      },
      error: (err) => {
        console.error('加载流程实例失败', err);
      },
    });
  }

  /**
   * 运行时实例为 definitionId；Camunda 历史实例为 processDefinitionId。
   */
  private getProcessDefinitionId(pi: ProcessInstance): string | undefined {
    return pi.definitionId ?? (pi as ProcessInstance & { processDefinitionId?: string }).processDefinitionId;
  }

  loadBpmnXml() {
    const definitionId = this.getProcessDefinitionId(this.processInstance());
    if (!definitionId) {
      console.error('无法解析流程定义 ID（definitionId / processDefinitionId）');
      return;
    }
    this.processInstanceService.getProcessDefinitionXml(definitionId).subscribe({
      next: (res) => {
        void this.loadXml(res.bpmn20Xml);
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
    viewer.importXML(trimmedXml);
  }
}
