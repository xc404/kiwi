import { Component, computed, input } from '@angular/core';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzTypographyModule } from 'ng-zorro-antd/typography';
import { BpmProcessInstanceDto } from '../service/process-instance.service';

@Component({
  selector: 'bpm-viewer-header',
  templateUrl: './bpm-viewer-header.component.html',
  styleUrl: './bpm-viewer-header.component.scss',
  imports: [NzTagModule, NzTypographyModule],
  standalone: true,
})
export class BpmViewerHeaderComponent {
  /** 流程实例详情；未加载完成时为 `undefined`。 */
  readonly processInstance = input<BpmProcessInstanceDto | undefined>(undefined);

  /** 已有实例 ID 且仍在拉取详情时展示加载文案。 */
  readonly loading = input(false);

  readonly statusLabel = computed(() => {
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

  readonly statusColor = computed(() => {
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
