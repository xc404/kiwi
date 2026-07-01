import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { toolbarAction } from '@app/shared/components/crud/actions';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { ColumnToken } from '@app/shared/components/table/column';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { firstValueFrom } from 'rxjs';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzMessageService } from 'ng-zorro-antd/message';

import { TemplatePackImportModalComponent } from './template-pack-import-modal.component';

@Component({
  selector: 'app-bpm-market',
  standalone: true,
  imports: [PageHeaderComponent, CrudPage, NzButtonModule],
  template: `
    <app-page-header></app-page-header>
    <section class="page-content">
      <crud-page [pageConfig]="pageConfig" />
    </section>
  `
})
export class BpmMarket {
  private readonly router = inject(Router);
  private readonly modalWrap = inject(NzModalWrapService);
  private readonly http = inject(BaseHttpService);
  private readonly message = inject(NzMessageService);

  pageConfig: PageConfig = {
    title: '模板市场',
    initializeData: true,
    crud: '/bpm/market',
    editModal: { disabled: true },
    toolbarActions: [
      toolbarAction({
        name: '导入模板包',
        icon: 'upload',
        tooltip: '上传 .kiwi-template-pack 并创建项目',
        handler: () => this.openImportModal()
      })
    ],
    columnActions: [
      {
        icon: 'eye',
        tooltip: '查看详情',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record?.id) {
            void this.router.navigate(['/bpm/market', record.id]);
          }
        }
      }
    ],
    fields: [
      { name: '名称', dataIndex: 'name' },
      { name: '摘要', dataIndex: 'summary' },
      { name: '类型', dataIndex: 'kind' },
      { name: '流程数', dataIndex: 'processCount' },
      { name: '分类', dataIndex: 'category' },
      { name: '安装次数', dataIndex: 'installCount' }
    ]
  };

  openImportModal(): void {
    const ref = this.modalWrap.create({
      nzTitle: '导入模板包',
      nzContent: TemplatePackImportModalComponent,
      nzOnOk: () => {
        const comp = ref.getContentComponent() as TemplatePackImportModalComponent;
        const fd = comp.tryGetFormData();
        if (!fd) {
          return false;
        }
        return firstValueFrom(
          this.http.post<{ projectId?: string }>('/bpm/market/import-and-install', fd, { needSuccessInfo: true })
        )
          .then((res: { projectId?: string }) => {
            if (res?.projectId) {
              void this.router.navigate(['/bpm/process-definition'], { queryParams: { projectId: res.projectId } });
            }
          })
          .catch((err: unknown) => {
            const e = err as { error?: { message?: string }; message?: string };
            this.message.error(e?.error?.message ?? e?.message ?? '导入失败');
            return Promise.reject(err);
          });
      }
    });
  }
}
