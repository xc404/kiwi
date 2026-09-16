import { Component, effect, inject, input, viewChild, ViewEncapsulation } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { tap } from 'rxjs/operators';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { AddAction, toolbarAction } from '@app/shared/components/crud/actions';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { FieldType } from '@app/shared/components/field/field';
import { ColumnToken } from '@app/shared/components/table/column';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzMessageService } from 'ng-zorro-antd/message';

import { BpmCloneProcessModalComponent } from './bpm-clone-process-modal.component';
import { ProcessDesignService } from '../design/service/process-design.service';
import { TemplatePackExportModalComponent } from '../market/template-pack-export-modal.component';
import { TemplatePackInstallModalComponent } from '../market/template-pack-install-modal.component';
import type { BpmProcess } from '../types/bpm-process';

@Component({
  selector: 'app-bpm-project-process-list',
  template: `
    <div class="bpm-process-section">
      <div class="bpm-process-section-header">
        <div class="bpm-process-section-title">流程定义</div>
        <div class="bpm-process-section-desc">在当前项目下创建、设计、部署与管理 BPM 流程</div>
      </div>
      <crud-page [pageConfig]="pageConfig"></crud-page>
    </div>
  `,
  imports: [CrudPage],
  styleUrls: ['./bpm-project-process-list.component.less'],
  encapsulation: ViewEncapsulation.None
})
export class BpmProjectProcessListComponent {
  readonly projectId = input.required<string>();
  readonly projectName = input('');

  private readonly router = inject(Router);
  private readonly http = inject(BaseHttpService);
  private readonly modalWrap = inject(NzModalWrapService);
  private readonly message = inject(NzMessageService);
  private readonly processDesignService = inject(ProcessDesignService);

  crudPage = viewChild(CrudPage);

  constructor() {
    effect(() => {
      const id = this.projectId();
      const page = this.crudPage();
      if (!id || !page) {
        return;
      }
      page.defaultEditRecord['projectId'] = id;
      queueMicrotask(() => {
        const p = this.crudPage();
        const current = this.projectId();
        if (!current || !p || current !== id) {
          return;
        }
        p.load({ projectId: current });
      });
    });
  }

  deployOne(record: { id?: string; name?: string }): void {
    const id = record?.id?.trim();
    if (!id) {
      return;
    }
    const displayName = record.name?.trim() || id;
    void this.runDeploy([{ id, name: displayName }]).then(result => {
      if (result.failed.length === 0) {
        this.message.success(`流程「${displayName}」部署成功`);
      } else {
        this.message.error(result.failed[0]?.message ?? `流程「${displayName}」部署失败`);
      }
    });
  }

  deploySelected(): void {
    const items = this.crudPage()?.selectedItems() ?? [];
    if (!items.length) {
      this.message.warning('请先勾选要部署的流程');
      return;
    }
    const targets = items
      .map((item: BpmProcess) => ({
        id: String(item.id ?? '').trim(),
        name: item.name?.trim() || String(item.id ?? '')
      }))
      .filter(t => t.id.length > 0);
    if (!targets.length) {
      this.message.warning('所选流程缺少有效 ID');
      return;
    }
    this.modalWrap.confirm({
      nzTitle: '部署所选流程',
      nzContent: `将部署 ${targets.length} 个流程到 Camunda 引擎，是否继续？`,
      nzOkText: '部署',
      nzCancelText: '取消',
      nzOnOk: () => this.runDeploy(targets).then(result => this.reportDeployResult(result))
    });
  }

  private async runDeploy(targets: Array<{ id: string; name: string }>): Promise<{ succeeded: string[]; failed: Array<{ name: string; message: string }> }> {
    const succeeded: string[] = [];
    const failed: Array<{ name: string; message: string }> = [];
    for (const target of targets) {
      try {
        await firstValueFrom(this.processDesignService.deployProcess(target.id));
        succeeded.push(target.name);
      } catch (err: unknown) {
        const e = err as { error?: { message?: string }; message?: string };
        failed.push({
          name: target.name,
          message: e?.error?.message ?? e?.message ?? '部署失败'
        });
      }
    }
    this.crudPage()?.reloadTable();
    return { succeeded, failed };
  }

  private reportDeployResult(result: { succeeded: string[]; failed: Array<{ name: string; message: string }> }): void {
    const { succeeded, failed } = result;
    if (failed.length === 0) {
      this.message.success(`已成功部署 ${succeeded.length} 个流程`);
      return;
    }
    if (succeeded.length === 0) {
      this.message.error(`部署失败：${failed.map(f => f.name).join('、')}`);
      return;
    }
    this.message.warning(`成功 ${succeeded.length} 个，失败 ${failed.length} 个（${failed.map(f => f.name).join('、')}）`);
  }

  openExportProjectTemplate(): void {
    const pid = this.projectId();
    const label = this.projectName() || pid;
    const ref = this.modalWrap.create({
      nzTitle: '导出项目为模板包',
      nzContent: TemplatePackExportModalComponent,
      nzData: { projectId: pid, defaultName: label },
      nzOnOk: () => {
        const comp = ref.getContentComponent() as TemplatePackExportModalComponent;
        const body = comp.tryGetPayload();
        if (!body) {
          return false;
        }
        return firstValueFrom(
          this.http.post(`/bpm/project/${pid}/export-as-template`, body, { needSuccessInfo: true })
        ).then(() => void this.router.navigate(['/bpm/market']));
      }
    });
  }

  openImportTemplateIntoProject(): void {
    const pid = this.projectId();
    const packId = prompt('请输入要导入的模板包 ID');
    if (!packId?.trim()) {
      return;
    }
    const ref = this.modalWrap.create({
      nzTitle: '导入模板到当前项目',
      nzContent: TemplatePackInstallModalComponent,
      nzData: { packName: packId, mode: 'intoProject' as const, targetProjectId: pid },
      nzOnOk: () => {
        const comp = ref.getContentComponent() as TemplatePackInstallModalComponent;
        const body = comp.tryGetPayload();
        if (!body) {
          return false;
        }
        return firstValueFrom(
          this.http.post(`/bpm/project/${pid}/import-template-pack/${packId.trim()}`, body, { needSuccessInfo: true })
        ).then(() => {
          const page = this.crudPage();
          if (page) {
            page.load({ projectId: pid });
          }
        });
      }
    });
  }

  openCloneModal(record: { id?: string; name?: string }): void {
    const id = record?.id;
    if (!id) {
      return;
    }
    const defaultName = `${record.name?.trim() || '未命名流程'} 副本`;
    const ref = this.modalWrap.create({
      nzTitle: '克隆流程',
      nzWidth: 480,
      nzOkText: '克隆',
      nzCancelText: '取消',
      nzContent: BpmCloneProcessModalComponent,
      nzData: { defaultName },
      nzOnOk: () => {
        const comp = ref.getContentComponent() as BpmCloneProcessModalComponent;
        const name = comp.tryGetName();
        if (!name) {
          return false;
        }
        const pid = this.projectId();
        return firstValueFrom(
          this.http.post(`/bpm/process/${id}/clone`, { name }, { needSuccessInfo: true }).pipe(
            tap(() => {
              const page = this.crudPage();
              if (page) {
                page.load({ projectId: pid });
              }
            })
          )
        ).catch((err: unknown) => {
          const e = err as { error?: { message?: string }; message?: string };
          this.message.error(e?.error?.message ?? e?.message ?? '克隆失败');
          return Promise.reject(err);
        });
      }
    });
  }

  pageConfig: PageConfig = {
    title: '流程定义',
    crud: '/bpm/process',
    tableConfig: {
      showCheckbox: true
    },
    toolbarActions: [
      AddAction,
      toolbarAction({
        name: '导出为模板',
        icon: 'export',
        tooltip: '将当前项目发布为模板包',
        handler: () => this.openExportProjectTemplate()
      }),
      toolbarAction({
        name: '导入模板',
        icon: 'import',
        tooltip: '将模板包合并进当前项目',
        handler: () => this.openImportTemplateIntoProject()
      }),
      toolbarAction({
        name: '部署所选',
        icon: 'cloud-upload',
        tooltip: '部署勾选的流程到 Camunda 引擎',
        handler: () => this.deploySelected()
      })
    ],
    columnActions: [
      {
        icon: 'deployment-unit',
        tooltip: '设计',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record?.id) {
            const url = new URL(window.location.href);
            url.hash = this.router.serializeUrl(this.router.createUrlTree(['/bpm/design', record.id]));
            window.open(url.toString(), '_blank', 'noopener,noreferrer');
          }
        }
      },
      {
        icon: 'unordered-list',
        tooltip: '流程实例',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record?.id) {
            this.router.navigate(['/bpm/process-instances'], {
              queryParams: { processDefinitionKey: record.id }
            });
          }
        }
      },
      {
        icon: 'copy',
        tooltip: '克隆',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record) {
            this.openCloneModal(record);
          }
        }
      },
      {
        icon: 'cloud-upload',
        tooltip: '部署',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record) {
            this.deployOne(record);
          }
        }
      }
    ],
    fields: [
      { name: '名称', dataIndex: 'name' },
      {
        name: '入口流程',
        dataIndex: 'entry',
        type: FieldType.Boolean,
        description: '勾选后该流程可以被外部系统调用'
      },
      {
        name: '项目ID',
        dataIndex: 'projectId',
        column: false,
        edit: {
          create: 'hidden',
          update: 'hidden'
        }
      }
    ]
  };
}
