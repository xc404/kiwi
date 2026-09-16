import { Component, computed, effect, inject, OnInit, signal, viewChild, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { tap } from 'rxjs/operators';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { AddAction, toolbarAction } from '@app/shared/components/crud/actions';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { FieldType } from '@app/shared/components/field/field';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { ColumnToken } from '@app/shared/components/table/column';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzAlertModule } from 'ng-zorro-antd/alert';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropdownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzTabsModule } from 'ng-zorro-antd/tabs';

import { BpmCloneProcessModalComponent } from './bpm-clone-process-modal.component';
import { BpmProjectEnvModalComponent } from './bpm-project-env-modal.component';
import { BpmProjectNameModalComponent } from './bpm-project-name-modal.component';
import { BpmWorkspaceService } from './bpm-workspace.service';
import { ProcessDesignService } from '../design/service/process-design.service';
import { TemplatePackExportModalComponent } from '../market/template-pack-export-modal.component';
import { TemplatePackImportModalComponent } from '../market/template-pack-import-modal.component';
import { TemplatePackInstallModalComponent } from '../market/template-pack-install-modal.component';
import type { BpmProcess } from '../types/bpm-process';

interface BpmProjectOption {
  id: string;
  name: string;
}

@Component({
  selector: 'app-bpm-project-process',
  template: `
    <app-page-header></app-page-header>
    <section class="page-content">
      <div class="bpm-workspace-toolbar m-b-16">
        <div class="bpm-workspace-toolbar-main">
          <span class="bpm-workspace-toolbar-icon" aria-hidden="true">
            <i nz-icon nzTheme="outline" nzType="folder-open"></i>
          </span>
          <div class="bpm-workspace-toolbar-text">
            <div class="bpm-workspace-toolbar-title">项目管理</div>
            <div class="bpm-workspace-toolbar-desc">切换项目以查看与管理该项目下的流程</div>
          </div>
        </div>
        <div class="bpm-workspace-toolbar-action">
          <span class="bpm-workspace-toolbar-label">当前项目</span>

          <a class="bpm-workspace-trigger" nz-dropdown nzOverlayClassName="bpm-workspace-project-menu-overlay" [nzDropdownMenu]="projectMenu">
            <span class="bpm-workspace-trigger-label">{{ currentProjectLabel() }}</span>
            <nz-icon nzType="down" />
          </a>
        </div>
      </div>

      <nz-dropdown-menu #projectMenu>
        <div class="bpm-workspace-dropdown" (click)="$event.stopPropagation()">
          <ul class="bpm-workspace-menu" nz-menu>
            @if (!filteredProjects().length) {
              <li class="bpm-workspace-menu-empty" nz-menu-item nzDisabled>
                {{ projects().length ? '无匹配项目' : '暂无项目' }}
              </li>
            } @else {
              @for (p of filteredProjects(); track p.id) {
                <li nz-menu-item [nzSelected]="p.id === projectId()" (click)="setProjectId(p.id)">
                  {{ p.name || p.id }}
                </li>
              }
            }
            <li nz-menu-divider></li>
            <li nz-menu-item (click)="openCreateProject()">新建项目</li>
            <li nz-menu-item [nzDisabled]="!projectId()" (click)="openRenameProject()">重命名</li>
            <li nz-menu-item [nzDisabled]="!projectId()" (click)="openProjectEnvModal()">环境变量</li>
            <li nz-menu-item [nzDisabled]="!projectId()" (click)="confirmDeleteProject()">删除项目</li>
            <li nz-menu-item (click)="openImportAsNewProject()">从模板新建</li>
          </ul>
        </div>
      </nz-dropdown-menu>

      @if (!projectsLoading() && projects().length === 0) {
        <nz-alert
          class="m-b-16"
          nzShowIcon
          nzType="info"
          nzMessage="还没有项目"
          nzDescription="新建一个项目后即可在其中创建流程、配置环境变量，或从模板包导入。"
        ></nz-alert>
        <button type="button" nz-button nzType="primary" class="m-r-8" (click)="openCreateProject()">新建项目</button>
        <button type="button" nz-button (click)="openImportAsNewProject()">从模板新建</button>
      } @else if (projectId()) {
        <nz-tabs>
          <nz-tab nzTitle="流程">
            <crud-page [pageConfig]="pageConfig"> </crud-page>
          </nz-tab>
        </nz-tabs>
      }
    </section>
  `,
  imports: [
    PageHeaderComponent,
    CrudPage,
    NzAlertModule,
    NzButtonModule,
    NzDropdownModule,
    NzIconModule,
    NzMenuModule,
    NzTabsModule
  ],
  styleUrls: ['./bpm-project-process.less'],
  encapsulation: ViewEncapsulation.None
})
export class BpmProjectProcess implements OnInit {
  private readonly activatedRoute = inject(ActivatedRoute);
  router = inject(Router);
  private readonly workspace = inject(BpmWorkspaceService);
  private readonly http = inject(BaseHttpService);
  private readonly modalWrap = inject(NzModalWrapService);
  private readonly message = inject(NzMessageService);
  private readonly processDesignService = inject(ProcessDesignService);

  projectId = signal<string | null>(null);
  readonly projectSearch = signal('');
  readonly projects = signal<BpmProjectOption[]>([]);
  readonly projectsLoading = signal(false);
  crudPage = viewChild(CrudPage);

  readonly filteredProjects = computed(() => {
    const q = this.projectSearch().trim().toLowerCase();
    const list = this.projects();
    if (!q) {
      return list;
    }
    return list.filter(p => (p.name || '').toLowerCase().includes(q) || p.id.toLowerCase().includes(q));
  });

  readonly currentProjectLabel = computed(() => {
    if (this.projectsLoading()) {
      return '加载中…';
    }
    const id = this.projectId();
    if (!id) {
      return '选择项目';
    }
    const p = this.projects().find(x => x.id === id);
    return p?.name || id;
  });

  constructor() {
    effect(() => {
      const id = this.projectId();
      const page = this.crudPage();
      if (id) {
        this.workspace.setLastProjectId(String(id));
      }
      if (!id || !page) {
        return;
      }
      page.defaultEditRecord['projectId'] = id;
      // CrudPage 使用 input.required(pageConfig)；effect 与 viewChild 就绪时子组件输入可能尚未绑定，
      // 立即 load() 会触发 NG0950。推迟到微任务后再访问子组件的 pageConfig signal。
      queueMicrotask(() => {
        const p = this.crudPage();
        const current = this.projectId();
        if (!current || !p || current !== id) {
          return;
        }
        p.load({ projectId: current });
      });
    });

    this.activatedRoute.queryParamMap.subscribe(qm => {
      let id = qm.get('projectId');
      if (!id) {
        id = this.workspace.getLastProjectId();
      }
      if (id) {
        this.projectId.set(id);
      }
    });
  }

  ngOnInit(): void {
    void this.loadProjects();
  }

  private async loadProjects(): Promise<void> {
    this.projectsLoading.set(true);
    try {
      const res = await firstValueFrom(
        this.http.get<{ content?: Array<{ id?: string; name?: string }> }>('/bpm/project', { page: 0, size: 500 })
      );
      const rows = res?.content ?? [];
      this.projects.set(
        rows
          .map(r => ({
            id: String(r.id ?? '').trim(),
            name: r.name ?? ''
          }))
          .filter(p => p.id.length > 0)
      );
      this.ensureProjectSelection();
    } finally {
      this.projectsLoading.set(false);
    }
  }

  private ensureProjectSelection(): void {
    const list = this.projects();
    const queryId = this.activatedRoute.snapshot.queryParamMap.get('projectId');
    const remembered = this.workspace.getLastProjectId();
    const preferred = queryId || remembered || this.projectId();
    const exists = (id: string | null | undefined) => !!id && list.some(p => p.id === id);

    if (!list.length) {
      if (remembered) {
        this.workspace.clearLastProjectId();
      }
      this.projectId.set(null);
      if (queryId) {
        void this.router.navigate([], {
          relativeTo: this.activatedRoute,
          queryParams: { projectId: null },
          queryParamsHandling: 'merge'
        });
      }
      return;
    }

    const next = exists(preferred) ? preferred! : list[0].id;
    if (!exists(preferred) && remembered && remembered !== next) {
      this.workspace.clearLastProjectId();
    }
    if (this.projectId() !== next || queryId !== next) {
      this.setProjectId(next);
    }
  }

  onProjectMenuVisible(visible: boolean): void {
    if (!visible) {
      this.projectSearch.set('');
    }
  }

  setProjectId(id: string): void {
    void this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: { projectId: id },
      queryParamsHandling: 'merge'
    });
  }

  openCreateProject(): void {
    const ref = this.modalWrap.create({
      nzTitle: '新建项目',
      nzWidth: 480,
      nzOkText: '创建',
      nzCancelText: '取消',
      nzContent: BpmProjectNameModalComponent,
      nzData: {
        hint: '项目是流程、环境变量与模板导入导出的容器。',
        label: '项目名称',
        defaultName: '',
        emptyWarning: '请填写项目名称'
      },
      nzOnOk: () => {
        const comp = ref.getContentComponent() as BpmProjectNameModalComponent;
        const name = comp.tryGetName();
        if (!name) {
          return false;
        }
        return firstValueFrom(this.http.post<BpmProjectOption>('/bpm/project', { name }, { needSuccessInfo: true })).then(
          created => {
            const id = String(created?.id ?? '').trim();
            if (!id) {
              this.message.error('创建项目失败：未返回项目 id');
              return;
            }
            this.projects.update(list => [...list, { id, name: created.name || name }]);
            this.setProjectId(id);
          }
        );
      }
    });
  }

  openRenameProject(): void {
    const pid = this.projectId();
    if (!pid) {
      this.message.warning('请先选择项目');
      return;
    }
    const currentName = this.projects().find(p => p.id === pid)?.name || '';
    const ref = this.modalWrap.create({
      nzTitle: '重命名项目',
      nzWidth: 480,
      nzOkText: '保存',
      nzCancelText: '取消',
      nzContent: BpmProjectNameModalComponent,
      nzData: {
        hint: '仅修改项目显示名称，不影响已有流程。',
        label: '项目名称',
        defaultName: currentName,
        emptyWarning: '请填写项目名称'
      },
      nzOnOk: () => {
        const comp = ref.getContentComponent() as BpmProjectNameModalComponent;
        const name = comp.tryGetName();
        if (!name) {
          return false;
        }
        return firstValueFrom(this.http.put<BpmProjectOption>(`/bpm/project/${pid}`, { name }, { needSuccessInfo: true })).then(
          updated => {
            this.projects.update(list => list.map(p => (p.id === pid ? { ...p, name: updated?.name || name } : p)));
          }
        );
      }
    });
  }

  openProjectEnvModal(): void {
    const pid = this.projectId();
    if (!pid) {
      this.message.warning('请先选择项目');
      return;
    }
    this.modalWrap.create({
      nzTitle: '环境变量',
      nzWidth: '75vw',
      nzContent: BpmProjectEnvModalComponent,
      nzData: { projectId: pid },
      nzFooter: null
    });
  }

  confirmDeleteProject(): void {
    const pid = this.projectId();
    if (!pid) {
      this.message.warning('请先选择项目');
      return;
    }
    const label = this.currentProjectLabel();
    this.modalWrap.confirm({
      nzTitle: '删除项目',
      nzContent: `将删除项目「${label}」及其环境变量。若项目下仍有流程，删除会失败。`,
      nzOkText: '删除',
      nzOkDanger: true,
      nzCancelText: '取消',
      nzOnOk: () => this.deleteCurrentProject(pid)
    });
  }

  private async deleteCurrentProject(pid: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/bpm/project/${pid}`, undefined, { needSuccessInfo: true }));
    if (this.workspace.getLastProjectId() === pid) {
      this.workspace.clearLastProjectId();
    }
    this.projects.update(list => list.filter(p => p.id !== pid));
    this.ensureProjectSelection();
  }

  openImportAsNewProject(): void {
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
            void this.loadProjects().then(() => this.setProjectId(res.projectId!));
          }
        });
      }
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
    if (!pid) {
      this.message.warning('请先选择项目');
      return;
    }
    const label = this.currentProjectLabel();
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
    if (!pid) {
      this.message.warning('请先选择项目');
      return;
    }
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
        return firstValueFrom(
          this.http.post(`/bpm/process/${id}/clone`, { name }, { needSuccessInfo: true }).pipe(
            tap(() => {
              const pid = this.projectId();
              const page = this.crudPage();
              if (page) {
                if (pid) {
                  page.load({ projectId: pid });
                } else {
                  page.reloadTable();
                }
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
    title: '项目管理',
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
