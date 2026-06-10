import { Component, effect, inject, OnInit, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { AppTableComponent } from '@app/shared/components/table/app-table/app-table.component';
import { AppTableConfig } from '@app/shared/components/table/table';
import { DictSelector } from '@shared/components/dict-selector/dict-selector';

import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalFooterDirective, NzModalRef } from 'ng-zorro-antd/modal';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzStepsModule } from 'ng-zorro-antd/steps';

@Component({
  selector: 'import-database-wizard',
  template: `
    @if (step() === 1) {
      @if (!jdbcConnectionsChecked()) {
        <div style="text-align: center; padding: 24px 0">
          <nz-spin nzSimple></nz-spin>
        </div>
      } @else if (jdbcConnectionsEmpty()) {
        <nz-alert nzDescription="请先在「数据库连接」页面创建 JDBC 连接，再导入数据库表。" nzMessage="暂无数据库连接" nzShowIcon nzType="warning"></nz-alert>
        <a class="m-t-16" routerLink="/tools/connection" nz-button nzType="primary" (click)="destroyModal()">前往创建数据库连接</a>
      } @else {
        <div style="width: 100%; text-align: center; ">
          <nz-form-label class="m-r-20" nz-form-label>数据库链接</nz-form-label>
          <app-dict-selector name="" [groupKey]="'jdbc-connections'" [model]="connection()" (modelChange)="connection.set($event)"></app-dict-selector>
        </div>
      }
    }
    @if (step() === 2) {
      <div>
        <app-table [loading]="tableLoading()" [tableConfig]="tableConfig" [tableData]="tables()"></app-table>
      </div>
    }
    <div *nzModalFooter>
      @if (step() === 2) {
        <button nz-button nzType="default" (click)="pre()">上一步</button>
      }
      @if (!(step() === 1 && (!jdbcConnectionsChecked() || jdbcConnectionsEmpty()))) {
        <button nz-button nzType="primary" (click)="next()">{{ step() === 1 ? '下一步' : '导入' }}</button>
      }
    </div>
  `,
  imports: [NzAlertModule, NzButtonModule, NzSpinModule, NzStepsModule, DictSelector, NzCardModule, NzModalFooterDirective, NzFormModule, AppTableComponent, RouterLink],
  standalone: true
})
export class ImportDatabaseWizardComponent implements OnInit {
  step = signal(1);
  http = inject(BaseHttpService);
  connection = signal<string | null>(null);
  jdbcConnectionsChecked = signal(false);
  jdbcConnectionsEmpty = signal(false);

  page = viewChild(AppTableComponent);

  tableConfig: AppTableConfig = {
    showCheckbox: true,
    yScroll: 400,
    columns: [
      { name: '名称', dataIndex: 'name' },
      { name: '描述', dataIndex: 'comment' }
    ]
  };

  tables = signal<any[]>([]);
  messageService = inject(NzMessageService);

  modal = inject(NzModalRef, { optional: true });
  tableLoading = signal<boolean>(false);

  constructor() {
    effect(() => {
      const currentStep = this.step();
      const connId = this.connection();
      if (currentStep === 2 && connId) {
        this.loadTables();
      }
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
        this.tables.set(res?.content ?? []);
      },
      complete: () => {
        this.tableLoading.set(false);
      }
    });
  }

  destroyModal(): void {
    this.modal?.destroy();
  }

  loadJdbcConnections(): void {
    this.http.get<{ content?: unknown[] }>('common/dict/jdbc-connections', { page: 0, size: 1000 }).subscribe({
      next: res => {
        this.jdbcConnectionsEmpty.set((res?.content?.length ?? 0) === 0);
        this.jdbcConnectionsChecked.set(true);
      }
    });
  }

  ngOnInit(): void {
    this.loadJdbcConnections();
  }

  pre() {
    this.step.set(1);
  }

  next() {
    if (this.step() === 1) {
      if (!this.connection()) {
        this.messageService.error('请选择数据库连接');
        return;
      }
      this.step.set(2);
    } else {
      const items = this.page()?.selectedItems();
      if (!items || items.length === 0) {
        this.messageService.error('请选择要导入的表');
        return;
      }
      this.http
        .post('tools/codegen/entity/import/database', {
          connectionId: this.connection(),
          tables: items.map(i => i.name)
        })
        .subscribe({
          next: () => {
            this.messageService.success('导入成功');
            this.destroyModal();
          },
          error: () => {
            this.messageService.error('导入失败');
          }
        });
    }
  }
}
