import { Component, inject, Injector, OnDestroy, OnInit, runInInjectionContext, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';

import { DictRecord } from '@app/shared/datastore/model/dict-record';
import { DictStoreService } from '@app/shared/datastore/dict-store.service';
import { FieldType, FieldTypeConfig, FormlyAttributes } from '@ngx-formly/core';
import { FormlyFieldProps } from '@ngx-formly/ng-zorro-antd/form-field';

import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { Subscription } from 'rxjs';

export type DictSelectMode = 'select' | 'radio';

export interface DictSelectProps extends FormlyFieldProps {
  /** 字典 storeId（= groupCode） */
  storeId?: string;
  /** @deprecated 使用 storeId */
  dictKey?: string;
  mode?: DictSelectMode;
  autoLoad?: boolean;
  pageSize?: number;
}

@Component({
  selector: 'formly-dict-select',
  template: `
    @if (mode() === 'radio') {
      <nz-radio-group [formControl]="formControl" [formlyAttributes]="field">
        @for (opt of dictOptions(); track opt.code) {
          <label nz-radio [nzValue]="opt.code">{{ opt.name }}</label>
        }
      </nz-radio-group>
    } @else {
      <nz-select
        nzAllowClear
        [formControl]="formControl"
        [formlyAttributes]="field"
        [nzDropdownRender]="dropdown"
        (nzScrollToBottom)="loadMore()"
      >
        @for (opt of dictOptions(); track opt.code) {
          <nz-option [nzLabel]="opt.name" [nzValue]="opt.code"></nz-option>
        }
      </nz-select>
      <ng-template #dropdown>
        @if (loading()) {
          <nz-spin nzSize="small"></nz-spin>
        }
      </ng-template>
    }
  `,
  imports: [ReactiveFormsModule, NzSelectModule, NzRadioModule, NzSpinModule, FormlyAttributes],
  standalone: true
})
export class DictSelectFieldType extends FieldType<FieldTypeConfig<DictSelectProps>> implements OnInit, OnDestroy {
  private readonly dictStoreService = inject(DictStoreService);
  private readonly injector = inject(Injector);

  dictOptions = signal<DictRecord[]>([]);
  loading = signal(false);
  mode = signal<DictSelectMode>('select');

  private itemsSub?: Subscription;
  private storeId?: string;

  ngOnInit(): void {
    this.storeId = this.props.storeId ?? this.props.dictKey;
    this.mode.set(this.props.mode === 'radio' ? 'radio' : 'select');
    if (!this.storeId) {
      return;
    }

    const store = this.dictStoreService.getStore({
      storeId: this.storeId,
      autoLoad: this.props.autoLoad ?? true,
      pageSize: this.props.pageSize
    });
    this.loading.set(store.loading());
    this.syncOptions(store);

    runInInjectionContext(this.injector, () => {
      this.itemsSub = toObservable(store.items).subscribe(() => {
        this.loading.set(store.loading());
        this.syncOptions(store);
      });
    });
  }

  ngOnDestroy(): void {
    this.itemsSub?.unsubscribe();
  }

  loadMore(): void {
    if (this.mode() !== 'select' || !this.storeId) {
      return;
    }
    const store = this.dictStoreService.getStore({ storeId: this.storeId });
    if (store.loading()) {
      return;
    }
    const page = store.items();
    if (page.length >= page.totalCount) {
      return;
    }
    store.nextPage();
  }

  private syncOptions(store: { getRecords: () => DictRecord[] }): void {
    const records = store.getRecords();
    if (records.length > 0) {
      this.dictOptions.set(records);
    }
  }
}
