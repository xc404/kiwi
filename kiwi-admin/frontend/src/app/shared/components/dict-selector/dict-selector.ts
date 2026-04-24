import { toObservable } from '@angular/core/rxjs-interop';
import { computed, inject, input, model, OnInit, signal } from "@angular/core";



import { Component } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { BaseHttpService } from "@app/core/services/http/base-http.service";
import { CrudDataSource } from "@app/shared/components/crud/crud-datastore";
import { CrudHttp } from "@app/shared/components/crud/crud-http";
import { NzSelectModule } from "ng-zorro-antd/select";
import { NzSpinModule } from "ng-zorro-antd/spin";
@Component({
  selector: 'app-dict-selector',
  template: `<nz-select style="minWidth: 200px"
      [(ngModel)]="model"
      (nzScrollToBottom)="loadMore()"
      nzPlaceHolder="{{name()}}"
      nzAllowClear
      [nzDropdownRender]="renderTemplate"
    >
      @for (item of optionList(); track item) {
        <nz-option [nzValue]="item.key" [nzLabel]="item.value"></nz-option>
      }
    </nz-select>
    
    <ng-template #renderTemplate>
      @if (loading()) {
        <nz-spin></nz-spin>
      }
    </ng-template>
    `,
  standalone: true,
  imports: [FormsModule, NzSelectModule, NzSpinModule],
})
export class DictSelector implements OnInit {
  http = inject(BaseHttpService);
  groupKey = input<string>();
  pageSize = input<number>(-1);
  name = input<string>();

  model = model<any>();;

  optionList = signal<Array<{ key: string; value: string }>>([]);
  loadMore(): void {

    this.loadDict();
  }

  pageDataSource = computed(() => {
    return new CrudDataSource<any>(new CrudHttp(this.http, '/common/dict/' + this.groupKey()), {
      pageSize: this.pageSize() > 0 ? this.pageSize() : 1000,
    });
  });

  data = computed(() => {
    return this.pageDataSource().items();
  });

  loading = computed(() => {
    return this.pageDataSource().loading();
  });

  constructor() {
    toObservable(this.data).subscribe(data => {
      this.optionList.set([...this.optionList(), ...data]);
    });
  }




  public loadDict(reset: boolean = false) {
    if (reset) {
      this.optionList.set([]);
      this.pageDataSource().page(0);
    } else {
      this.pageDataSource().nextPage();
    }
  }

  ngOnInit(): void {
    this.loadDict(true);
  }

}