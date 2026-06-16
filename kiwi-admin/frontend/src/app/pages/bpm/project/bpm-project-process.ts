import { Component, computed, effect, inject, OnInit, signal, viewChild, ViewEncapsulation } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { finalize, tap } from 'rxjs/operators';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { AddAction, toolbarAction } from '@app/shared/components/crud/actions';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { FieldType } from '@app/shared/components/field/field';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { ColumnToken } from '@app/shared/components/table/column';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropdownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzTabsModule } from 'ng-zorro-antd/tabs';

import { BpmCloneProcessModalComponent } from './bpm-clone-process-modal.component';
import { BpmProjectEnv } from './bpm-project-env';
import { BpmWorkspaceService } from './bpm-workspace.service';
import { ProcessDesignService } from '../design/service/process-design.service';
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
            <div class="bpm-workspace-toolbar-title">项目工作区</div>
            <div class="bpm-workspace-toolbar-desc">切换项目以查看与管理该项目下的流程</div>
          </div>
        </div>
        <div class="bpm-workspace-toolbar-action">
          <span class="bpm-workspace-toolbar-label">当前项目</span>

          <a #projectTrigger class="bpm-workspace-trigger" nz-dropdown nzOverlayClassName="bpm-workspace-project-menu-overlay" [nzDropdownMenu]="projectMenu">
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
          </ul>
        </div>
      </nz-dropdown-menu>

      <nz-tabs>
        <nz-tab nzTitle="流程">
          <crud-page [pageConfig]="pageConfig"> </crud-page>
        </nz-tab>
        <!-- 环境变量 
         <nz-tab nzTitle="环境变量">
           <app-bpm-project-env [projectId]="projectId()" />
         </nz-tab>

         -->
      </nz-tabs>
    </section>
  `,
  imports: [PageHeaderComponent, CrudPage, BpmProjectEnv, FormsModule, NzButtonModule, NzDropdownModule, NzIconModule, NzInputModule, NzMenuModule, NzTabsModule],
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
    this.loadProjects();
  }

  private loadProjects(): void {
    this.projectsLoading.set(true);
    this.http
      .get<{ content?: Array<{ id?: string; name?: string }> }>('/bpm/project', { page: 0, size: 500 })
      .pipe(finalize(() => this.projectsLoading.set(false)))
      .subscribe({
        next: res => {
          const rows = res?.content ?? [];
          this.projects.set(
            rows.map(r => ({
              id: String(r.id ?? ''),
              name: r.name ?? ''
            }))
          );
        }
      });
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
    title: '工作流',
    crud: '/bpm/process',
    tableConfig: {
      showCheckbox: true
    },
    toolbarActions: [
      AddAction,
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
        tooltip: '流程管理',
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
            this.router.navigate(['/bpm/processinstances'], {
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
        column: 'disabled',
        edit: {
          create: 'hidden',
          update: 'hidden'
        }
      }
    ]
  };
}
