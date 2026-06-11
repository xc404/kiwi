import { DatePipe, NgClass } from '@angular/common';
import { Component, EventEmitter, Output, computed, input } from '@angular/core';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzPopconfirmModule } from 'ng-zorro-antd/popconfirm';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

import { BPM_ACTIVITY_MARKER_LEGEND } from './bpm-activity-markers';
import { BpmProcessInstanceDto } from '../service/process-instance.service';

/** 工具栏错误区单行：可选节点名 + 说明 */
export interface BpmViewerToolbarErrorLine {
  nodeLabel: string | null;
  text: string;
}

@Component({
  selector: 'bpm-viewer-header',
  templateUrl: './bpm-viewer-header.component.html',
  styleUrl: './bpm-viewer-header.component.scss',
  imports: [DatePipe, NgClass, NzButtonModule, NzIconModule, NzPopconfirmModule, NzTagModule, NzTypographyModule],
  standalone: true
})
export class BpmViewerHeaderComponent {
  /** 流程实例详情；未加载完成时为 `undefined`。 */
  readonly processInstance = input<BpmProcessInstanceDto | undefined>(undefined);

  /** 已有实例 ID 且仍在拉取详情时展示加载文案。 */
  readonly loading = input(false);

  /** 一键恢复按钮处于请求中（避免重复点击与提示动画） */
  readonly recovering = input(false);

  /** 用户确认一键恢复 OPEN incident。 */
  @Output() readonly recoverRequested = new EventEmitter<void>();

  readonly activityMarkerLegend = BPM_ACTIVITY_MARKER_LEGEND;

  /** 仅在异常态（state=ERROR 或存在 OPEN incident）下展示恢复按钮 */
  readonly canRecover = computed(() => {
    const v = this.processInstance();
    if (!v?.id) {
      return false;
    }
    if (v.ended) {
      return false;
    }
    const state = String(v.state ?? '')
      .trim()
      .toUpperCase();
    if (state === 'COMPLETED' || state === 'CANCELED') {
      return false;
    }
    const hasOpenIncidents = (v.openIncidents?.length ?? 0) > 0;
    return state === 'ERROR' || hasOpenIncidents;
  });

  readonly statusLabel = computed(() => {
    const v = this.processInstance();
    if (!v) {
      return '';
    }
    const state = String(v.state ?? '')
      .trim()
      .toUpperCase();
    const hasOpenIncidents = (v.openIncidents?.length ?? 0) > 0;

    if (state === 'ERROR' || hasOpenIncidents) {
      return '异常';
    }
    if (v.suspended || state === 'SUSPENDED') {
      return '已挂起';
    }
    if (v.ended || state === 'COMPLETED' || state === 'CANCELED') {
      if (state === 'CANCELED') {
        return '已取消';
      }
      const dr = typeof v.deleteReason === 'string' ? v.deleteReason.trim() : '';
      if (dr) {
        return '已取消';
      }
      return '已结束';
    }
    return '运行中';
  });

  readonly statusColor = computed(() => {
    const v = this.processInstance();
    if (!v) {
      return 'default';
    }
    const state = String(v.state ?? '')
      .trim()
      .toUpperCase();
    const hasOpenIncidents = (v.openIncidents?.length ?? 0) > 0;

    if (state === 'ERROR' || hasOpenIncidents) {
      return 'error';
    }
    if (v.suspended || state === 'SUSPENDED') {
      return 'warning';
    }
    if (v.ended || state === 'COMPLETED' || state === 'CANCELED') {
      const dr = typeof v.deleteReason === 'string' ? v.deleteReason.trim() : '';
      const canceled = state === 'CANCELED' || !!dr;
      return canceled ? 'warning' : 'default';
    }
    return 'processing';
  });

  /**
   * 删除原因与未关闭 Incident（含节点名）；无内容时不展示错误区。
   */
  readonly errorLines = computed((): BpmViewerToolbarErrorLine[] => {
    const v = this.processInstance();
    if (!v) {
      return [];
    }
    const out: BpmViewerToolbarErrorLine[] = [];
    const dr = typeof v.deleteReason === 'string' ? v.deleteReason.trim() : '';
    if (dr) {
      out.push({ nodeLabel: null, text: dr });
    }
    const incidents = v.openIncidents;
    if (incidents?.length) {
      for (const inc of incidents) {
        const nameRaw = typeof inc.activityName === 'string' ? inc.activityName.trim() : '';
        const idRaw = typeof inc.activityId === 'string' ? inc.activityId.trim() : '';
        const nodeLabel = nameRaw || idRaw || null;
        const msg = typeof inc.message === 'string' ? inc.message.trim() : '';
        const typeStr = inc.incidentType != null ? String(inc.incidentType).trim() : '';
        const text = msg || typeStr || '—';
        out.push({ nodeLabel, text });
      }
    }
    return out;
  });

  readonly showErrorStrip = computed(() => this.errorLines().length > 0);

  /** 合并文案，供 title 等使用 */
  readonly errorSummaryText = computed(() =>
    this.errorLines()
      .map(l => (l.nodeLabel ? `${l.nodeLabel}：${l.text}` : l.text))
      .join('；')
  );

  /** 展示用流程名称：优先 API 名称，其次定义 Key */
  readonly processTitle = computed(() => {
    const v = this.processInstance();
    if (!v?.id) {
      return '';
    }
    const name = v.processDefinitionName?.trim();
    if (name) {
      return name;
    }
    const key = v.processDefinitionKey?.trim();
    if (key) {
      return key;
    }
    return '流程实例';
  });
}
