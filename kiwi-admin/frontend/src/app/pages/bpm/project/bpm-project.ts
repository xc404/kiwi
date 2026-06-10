import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';

import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { ColumnToken } from '@app/shared/components/table/column';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';

import { BpmProjectEnvModalComponent } from './bpm-project-env-modal.component';
import { BpmWorkspaceService } from './bpm-workspace.service';

@Component({
  selector: 'app-bpm-project',
  template: `
    <app-page-header></app-page-header>
    <section class="page-content">
      @if (lastWorkspaceId()) {
        <div class="m-b-16">
          <nz-alert nzDescription="点击下方按钮可回到上次打开的项目流程页" nzMessage="上次工作区" nzShowIcon nzType="info"></nz-alert>
          <button class="m-t-8" type="button" nz-button nzSize="small" nzType="primary" (click)="goLastWorkspace()"> 进入上次工作区 </button>
        </div>
      }
      <crud-page [pageConfig]="pageConfig"> </crud-page>
    </section>
  `,
  imports: [PageHeaderComponent, CrudPage, NzAlertModule, NzButtonModule]
})
export class BpmProject implements OnInit {
  router = inject(Router);
  private readonly workspace = inject(BpmWorkspaceService);
  private readonly modalWrap = inject(NzModalWrapService);

  /** 存在记忆时展示快捷入口 */
  readonly lastWorkspaceId = signal<string | null>(null);

  ngOnInit(): void {
    this.lastWorkspaceId.set(this.workspace.getLastProjectId());
  }

  goLastWorkspace(): void {
    const id = this.lastWorkspaceId();
    if (id) {
      void this.router.navigate(['/bpm/process-definition'], { queryParams: { projectId: id } });
    }
  }

  openProjectEnvModal(projectId: string): void {
    this.modalWrap.create({
      nzTitle: '环境变量',
      nzWidth: '75vw',
      nzContent: BpmProjectEnvModalComponent,
      nzData: { projectId },
      nzFooter: null
    });
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
            void this.router.navigate(['/bpm/process-definition'], { queryParams: { projectId: record.id } });
          }
        }
      },
      {
        icon: 'key',
        tooltip: '环境变量',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          const id = record?.id?.trim();
          if (id) {
            this.openProjectEnvModal(id);
          }
        }
      }
    ],
    crud: '/bpm/project',
    fields: [{ name: '名称', dataIndex: 'name' }]
  };
}
