import { Component } from '@angular/core';

import { FieldType, FieldTypeConfig, FormlyFieldProps } from '@ngx-formly/core';

import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzPopoverModule } from 'ng-zorro-antd/popover';

@Component({
  selector: 'bpm-declared-output-type',
  standalone: true,
  imports: [NzIconModule, NzPopoverModule],
  template: `
    <div class="declared-output-line">
      <span class="name-text">{{ displayName }}</span>
      <span
        class="help-icon"
        nz-icon
        nz-popover
        nzPopoverPlacement="bottom"
        nzPopoverTitle="详情"
        nzPopoverTrigger="click"
        nzTheme="outline"
        nzType="question-circle"
        [nzPopoverContent]="helpPopoverTpl"
      ></span>
    </div>
    <ng-template #helpPopoverTpl>
      <div class="help-popover-body">
        <div class="help-row">
          <span class="help-label">Key</span>
          <span class="help-value">{{ outputKey || '—' }}</span>
        </div>
        <div class="help-row">
          <span class="help-label">说明</span>
          <span class="help-value">{{ displayDescription || '—' }}</span>
        </div>
      </div>
    </ng-template>
  `,
  styles: [
    `
      .declared-output-line {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        min-height: 24px;
        line-height: 1.4;
      }
      .name-text {
        color: rgba(0, 0, 0, 0.88);
        font-size: 13px;
        font-weight: 500;
        word-break: break-word;
      }
      .help-icon {
        // color: rgba(0, 0, 0, 0.45);
        font-size: 14px;
        cursor: pointer;
      }
      .help-popover-body {
        max-width: 320px;
        font-size: 13px;
      }
      .help-row {
        display: flex;
        gap: 8px;
        align-items: flex-start;
        margin-bottom: 8px;
      }
      .help-row:last-child {
        margin-bottom: 0;
      }
      .help-label {
        flex: 0 0 auto;
        color: rgba(0, 0, 0, 0.45);
        min-width: 36px;
      }
      .help-value {
        flex: 1 1 auto;
        color: rgba(0, 0, 0, 0.88);
        word-break: break-word;
      }
    `
  ]
})
export class BpmDeclaredOutputType extends FieldType<FieldTypeConfig<FormlyFieldProps>> {
  get outputKey(): string {
    const key = this.field.key;
    return key ? String(key) : '';
  }

  get displayName(): string {
    return this.outputKey;
  }

  get displayDescription(): string {
    const d = this.props?.description;
    return d == null ? '' : String(d).trim();
  }
}
