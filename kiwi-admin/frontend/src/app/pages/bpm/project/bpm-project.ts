import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { ColumnToken } from '@app/shared/components/table/column';
import { toolbarAction } from '@app/shared/components/crud/actions';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzMessageService } from 'ng-zorro-antd/message';

import { BpmProjectEnvModalComponent } from './bpm-project-env-modal.component';
import { BpmWorkspaceService } from './bpm-workspace.service';
import { TemplatePackExportModalComponent } from '../market/template-pack-export-modal.component';
import { TemplatePackImportModalComponent } from '../market/template-pack-import-modal.component';

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
  private readonly http = inject(BaseHttpService);
  private readonly message = inject(NzMessageService);

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

  openImportTemplateModal(): void {
    const ref = this.modalWrap.create({
      nzTitle: '从模板包新建项目',
      nzContent: TemplatePackImportModalComponent,
      nzOnOk: () => {
        const comp = ref.getContentComponent() as TemplatePackImportModalComponent;
        const fd = comp.tryGetFormData();
        if (!fd) {
          return false;
        }
        return firstValueFrom(
          this.http.post<{ projectId?: string }>('/bpm/market/import-and-install', fd, { needSuccessInfo: true })
        ).then(res => {
          if (res?.projectId) {
            void this.router.navigate(['/bpm/process-definition'], { queryParams: { projectId: res.projectId } });
          }
        });
      }
    });
  }

  openExportTemplateModal(projectId: string, defaultName: string): void {
    const ref = this.modalWrap.create({
      nzTitle: '导出为模板包',
      nzContent: TemplatePackExportModalComponent,
      nzData: { projectId, defaultName },
      nzOnOk: () => {
        const comp = ref.getContentComponent() as TemplatePackExportModalComponent;
        const body = comp.tryGetPayload();
        if (!body) {
          return false;
        }
        return firstValueFrom(
          this.http.post(`/bpm/project/${projectId}/export-as-template`, body, { needSuccessInfo: true })
        ).then(() => {
          void this.router.navigate(['/bpm/market']);
        });
      }
    });
  }

  pageConfig: PageConfig = {
    title: '项目管理',
    initializeData: true,
    toolbarActions: [
      toolbarAction({
        name: '模板市场',
        icon: 'shop',
        tooltip: '浏览模板市场',
        handler: () => void this.router.navigate(['/bpm/market'])
      }),
      toolbarAction({
        name: '导入模板包',
        icon: 'upload',
        tooltip: '上传模板包并新建项目',
        handler: () => this.openImportTemplateModal()
      })
    ],
    columnActions: [
      {
        icon: 'deployment-unit',
        tooltip: '流程管理',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record?.id) {
            void this.router.navigate(['/bpm/process-definition'], { queryParams: { projectId: record.id } });
          }
        }
      },
      {
        icon: 'export',
        tooltip: '导出为模板',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record?.id) {
            this.openExportTemplateModal(record.id, record.name || '项目模板');
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
