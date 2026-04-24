import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';

import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { ColumnToken } from '@app/shared/components/table/column';
import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';

import { BpmWorkspaceService } from './bpm-workspace.service';

@Component({
  selector: 'app-bpm-project',
  template: `
    <app-page-header></app-page-header>
    <section class="page-content">
      @if (lastWorkspaceId()) {
        <div class="m-b-16">
          <nz-alert
            nzType="info"
            nzShowIcon
            nzMessage="上次工作区"
            nzDescription="点击下方按钮可回到上次打开的项目流程页"
          ></nz-alert>
          <button nz-button nzType="primary" nzSize="small" class="m-t-8" type="button" (click)="goLastWorkspace()">
            进入上次工作区
          </button>
        </div>
      }
      <crud-page [pageConfig]="pageConfig"> </crud-page>
    </section>
  `,
  imports: [PageHeaderComponent, CrudPage, NzAlertModule, NzButtonModule],
})
export class BpmProject implements OnInit {
  router = inject(Router);
  private readonly workspace = inject(BpmWorkspaceService);

  /** 存在记忆时展示快捷入口 */
  readonly lastWorkspaceId = signal<string | null>(null);

  ngOnInit(): void {
    this.lastWorkspaceId.set(this.workspace.getLastProjectId());
  }

  goLastWorkspace(): void {
    const id = this.lastWorkspaceId();
    if (id) {
      void this.router.navigate(['/bpm/process'], { queryParams: { projectId: id } });
    }
  }

  pageConfig: PageConfig = {
    title: '项目管理',
    initializeData: true,
    columnActions: [
      {
        icon: 'right-square',
        tooltip: '流程管理',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record?.id) {
            void this.router.navigate(['/bpm/process'], { queryParams: { projectId: record.id } });
          }
        },
      },
    ],
    crud: '/bpm/project',
    fields: [{ name: '名称', dataIndex: 'name' }],
  };
}
