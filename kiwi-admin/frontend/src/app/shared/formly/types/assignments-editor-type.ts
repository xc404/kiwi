import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FieldType, FieldTypeConfig, FormlyFieldProps } from '@ngx-formly/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { Subscription } from 'rxjs';

export interface AssignmentRow {
  key: string;
  /** 编辑中的值：字面量文本或 `${var}` */
  valueText: string;
}

interface AssignmentsProps extends FormlyFieldProps {
  /** 来自属性面板：运行时变量列表（含 name） */
  variables?: Array<{ name?: string | null; [key: string]: unknown }>;
}

function parseAssignments(raw: unknown): AssignmentRow[] {
  if (raw == null || raw === '') {
    return [{ key: '', valueText: '' }];
  }
  let list: unknown[] = [];
  if (typeof raw === 'string') {
    const t = raw.trim();
    if (!t) {
      return [{ key: '', valueText: '' }];
    }
    try {
      const parsed = JSON.parse(t);
      list = Array.isArray(parsed) ? parsed : [];
    } catch {
      return [{ key: '', valueText: t }];
    }
  } else if (Array.isArray(raw)) {
    list = raw;
  } else {
    return [{ key: '', valueText: '' }];
  }
  if (list.length === 0) {
    return [{ key: '', valueText: '' }];
  }
  return list.map((item: any) => ({
    key: String(item?.key ?? ''),
    valueText: formatValueForEdit(item?.value),
  }));
}

function formatValueForEdit(value: unknown): string {
  if (value === undefined || value === null) {
    return '';
  }
  if (typeof value === 'string') {
    return value;
  }
  return JSON.stringify(value);
}

function parseValueText(text: string): unknown {
  const t = text.trim();
  if (t === '') {
    return '';
  }
  if (/^\$\{[a-zA-Z0-9_]+\}$/.test(t)) {
    return t;
  }
  if (t === 'true') {
    return true;
  }
  if (t === 'false') {
    return false;
  }
  if (t === 'null') {
    return null;
  }
  if (/^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(t)) {
    const n = Number(t);
    if (!Number.isNaN(n)) {
      return n;
    }
  }
  try {
    return JSON.parse(t);
  } catch {
    return t;
  }
}

@Component({
  selector: 'assignments-editor-type',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    NzButtonModule,
    NzIconModule,
    NzInputModule,
    NzSelectModule,
    NzTooltipModule,
  ],
  template: `
    <div class="assignments-editor">
      @if (field.props.readonly) {
        @if (payloadRows.length === 0) {
          <span class="text-muted">—</span>
        } @else {
          <ul class="assignments-readonly">
            @for (r of payloadRows; track $index) {
              <li>
                <code>{{ r.key }}</code>
                <span class="sep">←</span>
                <code>{{ formatDisplayValue(r.value) }}</code>
              </li>
            }
          </ul>
        }
      } @else {
        <div class="assignments-toolbar">
          <button nz-button nzType="dashed" nzSize="small" type="button" (click)="addRow()">
            <nz-icon nzType="plus" nzTheme="outline" />
            添加赋值
          </button>
        </div>
        @for (row of rows; track $index; let i = $index) {
          <div class="assignment-row">
            <input
              nz-input
              class="key-in"
              placeholder="目标变量名"
              [ngModel]="row.key"
              (ngModelChange)="onKeyChange(i, $event)"
            />
            <input
              nz-input
              class="val-in"
              placeholder="字面量，或 \${源变量名} 复制变量"
              [ngModel]="row.valueText"
              (ngModelChange)="onValueChange(i, $event)"
            />
            @if (varNames.length) {
              <nz-select
                class="var-ref"
                nzPlaceHolder="引用变量"
                nzAllowClear
                [ngModel]="refSelection[i]"
                (ngModelChange)="onRefSelect(i, $event)"
              >
                @for (n of varNames; track n) {
                  <nz-option [nzValue]="n" [nzLabel]="n"></nz-option>
                }
              </nz-select>
            }
            <button
              nz-button
              nzType="text"
              nzSize="small"
              type="button"
              nz-tooltip
              nzTooltipTitle="复制本行"
              (click)="duplicateRow(i)"
            >
              <nz-icon nzType="copy" nzTheme="outline" />
            </button>
            <button
              nz-button
              nzType="text"
              nzSize="small"
              nzDanger
              type="button"
              [disabled]="rows.length <= 1"
              (click)="removeRow(i)"
            >
              <nz-icon nzType="delete" nzTheme="outline" />
            </button>
          </div>
        }
      }
    </div>
  `,
  styles: [
    `
      .assignments-editor {
        width: 100%;
      }
      .assignments-toolbar {
        margin-bottom: 8px;
      }
      .assignment-row {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 8px;
        margin-bottom: 8px;
      }
      .key-in {
        flex: 1 1 120px;
        min-width: 100px;
      }
      .val-in {
        flex: 2 1 180px;
        min-width: 140px;
      }
      .var-ref {
        flex: 1 1 100px;
        min-width: 96px;
        max-width: 160px;
      }
      .assignments-readonly {
        margin: 0;
        padding-left: 1.2em;
      }
      .assignments-readonly li {
        margin-bottom: 4px;
      }
      .assignments-readonly .sep {
        margin: 0 6px;
        opacity: 0.65;
      }
      .text-muted {
        opacity: 0.55;
      }
    `,
  ],
})
export class AssignmentsEditorType
  extends FieldType<FieldTypeConfig<AssignmentsProps>>
  implements OnInit, OnDestroy
{
  rows: AssignmentRow[] = [{ key: '', valueText: '' }];
  /** 每行 nz-select 受控值，避免与自由输入冲突 */
  refSelection: (string | null)[] = [];
  private sub?: Subscription;
  private syncing = false;

  get varNames(): string[] {
    const raw = this.field.props?.variables;
    if (!Array.isArray(raw)) {
      return [];
    }
    const names = raw
      .map((v) => (v?.name != null ? String(v.name).trim() : ''))
      .filter((n) => n.length > 0);
    return [...new Set(names)];
  }

  get payloadRows(): { key: string; value: unknown }[] {
    return this.rows
      .filter((r) => r.key.trim() !== '')
      .map((r) => ({ key: r.key.trim(), value: parseValueText(r.valueText) }));
  }

  ngOnInit(): void {
    this.rows = parseAssignments(this.formControl.value);
    this.syncRefSelections();
    this.sub = this.formControl.valueChanges.subscribe((v) => {
      if (this.syncing) {
        return;
      }
      this.rows = parseAssignments(v);
      this.syncRefSelections();
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private syncRefSelections(): void {
    this.refSelection = this.rows.map((r) => {
      const t = r.valueText.trim();
      const m = /^\$\{([a-zA-Z0-9_]+)\}$/.exec(t);
      if (m && this.varNames.includes(m[1])) {
        return m[1];
      }
      return null;
    });
  }

  formatDisplayValue(value: unknown): string {
    if (value === undefined) {
      return '';
    }
    if (typeof value === 'string') {
      return value;
    }
    return JSON.stringify(value);
  }

  onKeyChange(index: number, value: string): void {
    this.rows[index] = { ...this.rows[index], key: value };
    this.emit();
  }

  onValueChange(index: number, value: string): void {
    this.rows[index] = { ...this.rows[index], valueText: value };
    this.refSelection[index] = null;
    this.emit();
  }

  onRefSelect(index: number, varName: string | null): void {
    if (varName == null || varName === '') {
      return;
    }
    this.rows[index] = {
      ...this.rows[index],
      valueText: '${' + varName + '}',
    };
    this.refSelection[index] = varName;
    this.emit();
  }

  addRow(): void {
    this.rows = [...this.rows, { key: '', valueText: '' }];
    this.refSelection = [...this.refSelection, null];
    this.emit();
  }

  removeRow(index: number): void {
    if (this.rows.length <= 1) {
      this.rows = [{ key: '', valueText: '' }];
      this.refSelection = [null];
      this.emit();
      return;
    }
    this.rows = this.rows.filter((_, i) => i !== index);
    this.refSelection = this.refSelection.filter((_, i) => i !== index);
    this.emit();
  }

  duplicateRow(index: number): void {
    const r = this.rows[index];
    this.rows = [
      ...this.rows.slice(0, index + 1),
      { key: r.key, valueText: r.valueText },
      ...this.rows.slice(index + 1),
    ];
    this.syncRefSelections();
    this.emit();
  }

  private emit(): void {
    const payload = this.payloadRows;
    const json = JSON.stringify(payload);
    this.syncing = true;
    this.formControl.setValue(json, { emitEvent: true });
    this.syncing = false;
    this.formControl.markAsDirty();
    this.formControl.markAsTouched();
  }
}
