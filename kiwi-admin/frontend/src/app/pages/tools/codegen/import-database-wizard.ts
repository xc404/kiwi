import { Component, effect, inject, signal, viewChild } from "@angular/core";
import { BaseHttpService } from "@app/core/services/http/base-http.service";
import { AppTableComponent } from "@app/shared/components/table/app-table/app-table.component";
import { AppTableConfig } from "@app/shared/components/table/table";
import { DictSelector } from "@app/widget/biz-widget/system/dict-selector/dict-selector";
import { FormlyForm } from "@ngx-formly/core";
import { NzButtonModule } from "ng-zorro-antd/button";
import { NzCardModule } from "ng-zorro-antd/card";
import { NzFormModule } from "ng-zorro-antd/form";
import { NzMessageService } from "ng-zorro-antd/message";
import { NzModalFooterDirective, NzModalRef } from "ng-zorro-antd/modal";
import { NzStepsModule } from "ng-zorro-antd/steps";

@Component({
  selector: 'import-database-wizard',
  template: `
  @if (step === 1) {
    <div style="width: 100%; text-align: center; ">
      <nz-form-label nz-form-label class="m-r-20">数据库链接</nz-form-label>
        <app-dict-selector [groupKey]="'jdbc-connections'" name="" [(model)]="connection" ></app-dict-selector>
    </div>
  }
  @if(step === 2   ) {
    <div >
      <app-table [tableData]="tables()" [tableConfig]="tableConfig" [loading]="tableLoading()" ></app-table>
      </div>
  }
      <div *nzModalFooter>
        @if (step === 2) { 
          <button nz-button nzType="default" (click)="pre()">上一步</button>
        }
      <button nz-button nzType="primary" (click)="next()">确定</button>
    </div>
  
            `,
  imports: [NzButtonModule, NzStepsModule, DictSelector, NzCardModule, NzModalFooterDirective, NzFormModule, AppTableComponent],
  standalone: true
})
export class ImportDatabaseWizardComponent {
  step = 2;
  http = inject(BaseHttpService);
  connection = signal<string | null>("6943758352c8d068f11b4fda");

  page = viewChild(AppTableComponent);

  tableConfig: AppTableConfig = {
    showCheckbox: true,
    yScroll: 400,
    columns: [
      { name: '名称', dataIndex: 'name' },
      { name: '描述', dataIndex: 'comment' },
    ],
  }

  tables = signal<any[]>([]);
  messageService = inject(NzMessageService);

  modal = inject(NzModalRef, { optional: true });
  tableLoading = signal<boolean>(false);

  constructor() {
    effect(() => {
      this.loadTables();
    });
  }
  loadTables() {

    const connId = this.connection();
    if (!connId) {
      this.tables.set([]);
      return;
    }
    this.tableLoading.set(true);
    this.http.get(`tools/connection/${connId}/tables`).subscribe({
      next: (res: any) => {
        this.tables.set(res.content || []);
      },
      complete: () => {
        this.tableLoading.set(false);
      }
    });
  }

  destroyModal(): void {
    this.modal?.destroy();
  }

  pre() {
    this.step = 1;
  }

  next() {
    if (this.step === 1) {
      if (!this.connection()) {
        this.messageService.error("请选择数据库连接");
        return;
      }
      this.step = 2;
    } else {
      let items = this.page()?.selectedItems();
      if (!items || items.length === 0) {
        this.messageService.error("请选择要导入的表");
        return;
      }
      this.http.post('tools/codegen/entity/import/database', {
        connectionId: this.connection(),
        tables: items.map(i => i.name)
      }).subscribe({
        next: () => {
          this.messageService.success("导入成功");
          this.destroyModal();
        },
        error: (err) => {
          this.messageService.error("导入失败");
        }
      });
    }
  }


}