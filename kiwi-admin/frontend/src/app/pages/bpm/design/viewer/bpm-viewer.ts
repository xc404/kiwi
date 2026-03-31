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
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import { Element } from 'bpmn-js/lib/model/Types';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { firstValueFrom } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { BpmPropertiesPanel } from '../property-panel/properties-panel';
import {
  ProcessInstanceDiagramView,
  ProcessInstanceService,
  ProcessVariableRow,
} from '../service/process-instance.service';
import { NzLayoutComponent, NzLayoutModule } from 'ng-zorro-antd/layout';

@Component({
  selector: 'bpm-viewer',
  templateUrl: './bpm-viewer.html',
  styleUrl: './bpm-viewer.scss',
  imports: [
    PageHeaderComponent,
    BpmPropertiesPanel,
    NzSpinModule,
    NzTagModule,
    NzLayoutComponent,
    NzLayoutModule,
  ],
  standalone: true,
})
export class BpmViewer implements AfterViewInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly processInstanceService = inject(ProcessInstanceService);

  @ViewChild('canvasHost', { static: true }) canvasHost!: ElementRef<HTMLElement>;

  protected readonly loading = signal(false);
  protected readonly view = signal<ProcessInstanceDiagramView | null>(null);
  /** 与 BpmPropertiesPanel 一致：当前选中的图元 */
  protected readonly selectedElement = signal<Element | null>(null);
  /** 是否选中画布根（流程级变量） */
  protected readonly selectionIsRoot = signal(true);

  protected readonly statusLabel = computed(() => {
    const v = this.view();
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
    const v = this.view();
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

  /** 按选中 Activity / 流程根过滤后的变量 */
  /** 当前选中高亮 marker 所在图元 id（与 kiwi-bpmn-selected 对应） */
  private selectionMarkerElementId: string | null = null;

  protected readonly filteredVariables = computed((): ProcessVariableRow[] => {
    const v = this.view();
    if (!v) {
      return [];
    }
    const vars = v.variables;
    if (this.selectionIsRoot()) {
      return vars.filter((r) => !r.activityInstanceId);
    }
    const el = this.selectedElement();
    if (!el) {
      return [];
    }
    const ids = v.activityInstanceIdsByActivityId[el.id];
    if (!ids?.length) {
      return [];
    }
    const idSet = new Set(ids);
    return vars.filter((r) => r.activityInstanceId != null && idSet.has(r.activityInstanceId));
  });

  protected viewer: NavigatedViewer | null = null;

  ngAfterViewInit(): void {
    this.viewer = new NavigatedViewer({
      container: this.canvasHost.nativeElement,
    });
    this.bindSelectionChanged();

    this.route.paramMap
      .pipe(
        map((p) => p.get('processInstanceId')),
        filter((id): id is string => !!id),
      )
      .subscribe((id) => {
        void this.loadDiagram(id);
      });
  }

  ngOnDestroy(): void {
    this.viewer?.destroy();
    this.viewer = null;
  }

  private bindSelectionChanged(): void {
    if (!this.viewer) {
      return;
    }
    this.viewer.on('selection.changed', (e: { newSelection: Element[] }) => {
      let el = e.newSelection[e.newSelection.length - 1];
      if (!el) {
        const canvas = this.viewer!.get('canvas') as { getRootElement: () => Element };
        el = canvas.getRootElement();
      }
      this.applySelectionMarker(el);
      this.selectedElement.set(el);
      const canvas = this.viewer!.get('canvas') as { getRootElement: () => Element };
      const root = canvas.getRootElement();
      this.selectionIsRoot.set(el.id === root.id);
    });
  }

  private clearSelectionMarker(): void {
    if (!this.viewer || !this.selectionMarkerElementId) {
      this.selectionMarkerElementId = null;
      return;
    }
    const canvas = this.viewer.get('canvas') as {
      removeMarker: (elementId: string, marker: string) => void;
    };
    const elementRegistry = this.viewer.get('elementRegistry') as {
      get: (id: string) => unknown;
    };
    if (elementRegistry.get(this.selectionMarkerElementId)) {
      canvas.removeMarker(this.selectionMarkerElementId, 'kiwi-bpmn-selected');
    }
    this.selectionMarkerElementId = null;
  }

  /** 选中图元描边高亮（与流程态 marker 共存，选中色优先） */
  private applySelectionMarker(el: Element): void {
    if (!this.viewer) {
      return;
    }
    this.clearSelectionMarker();
    const canvas = this.viewer.get('canvas') as {
      addMarker: (elementId: string, marker: string) => void;
    };
    const elementRegistry = this.viewer.get('elementRegistry') as {
      get: (id: string) => unknown;
    };
    if (!el?.id || !elementRegistry.get(el.id)) {
      return;
    }
    canvas.addMarker(el.id, 'kiwi-bpmn-selected');
    this.selectionMarkerElementId = el.id;
  }

  private async loadDiagram(processInstanceId: string): Promise<void> {
    if (!this.viewer) {
      return;
    }
    this.loading.set(true);
    this.view.set(null);
    try {
      const data = await firstValueFrom(
        this.processInstanceService.getDiagramView(processInstanceId),
      );
      this.view.set(data);
      this.clearSelectionMarker();
      await this.viewer.importXML(data.bpmnXml);
      this.applyMarkers(data);
      const canvas = this.viewer.get('canvas') as { getRootElement: () => Element };
      const root = canvas.getRootElement();
      const selection = this.viewer.get('selection') as { select: (e: Element) => void };
      selection.select(root);
      this.applySelectionMarker(root);
      this.selectedElement.set(root);
      this.selectionIsRoot.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  private applyMarkers(data: ProcessInstanceDiagramView): void {
    if (!this.viewer) {
      return;
    }
    const canvas = this.viewer.get('canvas') as {
      addMarker: (elementId: string, marker: string) => void;
    };
    const elementRegistry = this.viewer.get('elementRegistry') as {
      get: (id: string) => unknown;
    };

    for (const id of data.completedActivityIds) {
      if (elementRegistry.get(id)) {
        canvas.addMarker(id, 'kiwi-bpmn-completed');
      }
    }
    for (const id of data.activeActivityIds) {
      if (elementRegistry.get(id)) {
        canvas.addMarker(id, 'kiwi-bpmn-active');
      }
    }
  }

}
