import { Component, computed, effect, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { DictStore } from '@app/shared/datastore/dict-store';
import { DictStoreService } from '@app/shared/datastore/dict-store.service';

import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSpinModule } from 'ng-zorro-antd/spin';

@Component({
  selector: 'app-dict-selector',
  template: `<nz-select nzAllowClear nzPlaceHolder="{{ name() }}" style="min-width: 200px" [nzDropdownRender]="renderTemplate" [(ngModel)]="model" (nzScrollToBottom)="loadMore()">
      @for (item of optionList(); track item.key) {
        <nz-option [nzLabel]="item.value" [nzValue]="item.key"></nz-option>
      }
    </nz-select>

    <ng-template #renderTemplate>
      @if (loading()) {
        <nz-spin></nz-spin>
      }
    </ng-template> `,
  standalone: true,
  imports: [FormsModule, NzSelectModule, NzSpinModule]
})
export class DictSelector {
  private dictStoreService = inject(DictStoreService);

  /** 字典 storeId（= groupCode） */
  storeId = input.required<string>();
  pageSize = input<number>(-1);
  name = input<string>();

  model = model<any>();

  optionList = signal<Array<{ key: string; value: string }>>([]);
  dictStore = signal<DictStore | null>(null);

  loading = computed(() => this.dictStore()?.loading() ?? false);

  constructor() {
    effect(() => {
      const id = this.storeId();
      const size = this.pageSize() > 0 ? this.pageSize() : undefined;
      if (!id) {
        return;
      }

      const store = this.dictStoreService.getStore({
        storeId: id,
        autoLoad: true,
        pageSize: size
      });
      this.dictStore.set(store);
      this.optionList.set([]);
      if (store.records().length === 0) {
        store.load(0);
      } else {
        this.syncOptions(store);
      }
    });

    effect(() => {
      const store = this.dictStore();
      if (!store) {
        return;
      }
      store.items();
      this.syncOptions(store);
    });
  }

  loadMore(): void {
    this.loadDict();
  }

  loadDict(reset = false): void {
    const store = this.dictStore();
    if (!store) {
      return;
    }
    if (reset) {
      this.optionList.set([]);
      store.load(0);
    } else {
      store.nextPage();
    }
  }

  private syncOptions(store: DictStore): void {
    const mapped = store.getRecords().map(item => ({
      key: item.code,
      value: item.name
    }));
    if (mapped.length === 0) {
      return;
    }
    this.optionList.set(mapped);
  }
}
