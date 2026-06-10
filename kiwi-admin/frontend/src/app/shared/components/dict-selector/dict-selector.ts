import { Component, computed, effect, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { CrudDataSource, Page } from '@app/shared/components/crud/crud-datastore';
import { CrudHttp } from '@app/shared/components/crud/crud-http';

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
  http = inject(BaseHttpService);
  groupKey = input<string>();
  pageSize = input<number>(-1);
  name = input<string>();

  model = model<any>();

  optionList = signal<Array<{ key: string; value: string }>>([]);
  dataSource = signal<CrudDataSource<any> | null>(null);

  loading = computed(() => this.dataSource()?.loading() ?? false);

  constructor() {
    effect(() => {
      const key = this.groupKey();
      const size = this.pageSize() > 0 ? this.pageSize() : 1000;
      if (!key) {
        return;
      }

      const ds = new CrudDataSource<any>(new CrudHttp(this.http, `/common/dict/${key}`), { pageSize: size });
      this.dataSource.set(ds);
      this.optionList.set([]);
      ds.page(0);
    });

    effect(() => {
      const ds = this.dataSource();
      if (!ds) {
        return;
      }
      const page = ds.items() as Page<{ code?: string; name?: string; key?: string; value?: string }>;
      const mapped = Array.from(page ?? []).map(item => ({
        key: String(item.code ?? item.key ?? ''),
        value: String(item.name ?? item.value ?? '')
      }));
      if (mapped.length === 0) {
        return;
      }
      const pageIndex = page.pageIndex ?? 0;
      if (pageIndex === 0) {
        this.optionList.set(mapped);
      } else {
        this.optionList.update(list => [...list, ...mapped]);
      }
    });
  }

  loadMore(): void {
    this.loadDict();
  }

  loadDict(reset: boolean = false): void {
    const ds = this.dataSource();
    if (!ds) {
      return;
    }
    if (reset) {
      this.optionList.set([]);
      ds.page(0);
    } else {
      ds.nextPage();
    }
  }
}
