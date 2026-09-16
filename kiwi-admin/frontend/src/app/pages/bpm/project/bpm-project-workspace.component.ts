import { Component, computed, effect, inject, input, OnInit, output, signal, ViewEncapsulation } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropdownModule } from 'ng-zorro-antd/dropdown';
import { NzEmptyModule } from 'ng-zorro-antd/empty';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';

import { BpmProjectEnvModalComponent } from './bpm-project-env-modal.component';
import { BpmProjectNameModalComponent } from './bpm-project-name-modal.component';
import type { BpmProjectOption } from './bpm-project.types';
import { BpmWorkspaceService } from './bpm-workspace.service';
import { TemplatePackImportModalComponent } from '../market/template-pack-import-modal.component';

@Component({
  selector: 'app-bpm-project-workspace',
  template: `
    <section class="bpm-workspace-panel">
      <div class="bpm-workspace-body">
        @if (projectsLoading()) {
          <div class="bpm-workspace-loading">
            <nz-spin nzSimple nzTip="加载项目列表…"></nz-spin>
          </div>
        } @else if (projects().length === 0) {
          <div class="bpm-workspace-empty">
            <nz-empty nzNotFoundImage="simple" nzNotFoundContent="还没有项目"></nz-empty>
            <p class="bpm-workspace-empty-desc">新建项目或从模板包导入后，即可创建流程并配置环境变量。</p>
            <div class="bpm-workspace-empty-actions">
              <button type="button" nz-button nzType="primary" (click)="openCreateProject()">
                <span nz-icon nzType="plus"></span>
                新建项目
              </button>
              <button type="button" nz-button (click)="openImportAsNewProject()">
                <span nz-icon nzType="import"></span>
                从模板新建
              </button>
            </div>
          </div>
        } @else {
          <div class="bpm-workspace-row">
            <div class="bpm-workspace-picker">
              <span class="bpm-workspace-picker-label">当前项目</span>
              <button
                type="button"
                class="bpm-workspace-picker-btn"
                nz-button
                nz-dropdown
                nzTrigger="click"
                nzOverlayClassName="bpm-workspace-project-menu-overlay"
                [nzDropdownMenu]="projectMenu"
                (nzVisibleChange)="onProjectMenuVisible($event)"
              >
                <span nz-icon nzTheme="outline" nzType="project"></span>
                <span class="bpm-workspace-picker-name">{{ currentProjectLabel() }}</span>
                <span nz-icon nzType="down"></span>
              </button>
            </div>

            <div class="bpm-workspace-actions">
              <button type="button" nz-button nzType="primary" (click)="openCreateProject()">
                <span nz-icon nzType="plus"></span>
                新建
              </button>
              <button type="button" nz-button [disabled]="!projectId()" nz-tooltip nzTooltipTitle="按项目配置流程运行所需变量" (click)="openProjectEnvModal()">
                <span nz-icon nzType="setting"></span>
                环境变量
              </button>
              <button type="button" nz-button nz-dropdown [nzDropdownMenu]="moreMenu">
                更多
                <span nz-icon nzType="down"></span>
              </button>
            </div>
          </div>
        }
      </div>
    </section>

    <nz-dropdown-menu #projectMenu="nzDropdownMenu">
      <div class="bpm-workspace-dropdown" (click)="$event.stopPropagation()">
        <nz-input-group class="bpm-workspace-search" nzPrefixIcon="search">
          <input
            nz-input
            placeholder="搜索项目名称或 ID"
            [ngModel]="projectSearch()"
            (ngModelChange)="projectSearch.set($event)"
            (click)="$event.stopPropagation()"
          />
        </nz-input-group>
        <ul class="bpm-workspace-menu" nz-menu>
          @if (!filteredProjects().length) {
            <li class="bpm-workspace-menu-empty" nz-menu-item nzDisabled>
              {{ projects().length ? '无匹配项目' : '暂无项目' }}
            </li>
          } @else {
            @for (p of filteredProjects(); track p.id) {
              <li nz-menu-item [nzSelected]="p.id === projectId()" (click)="selectProject(p.id)">
                <span class="bpm-workspace-menu-item-name">{{ p.name || '未命名项目' }}</span>
                @if (p.name) {
                  <span class="bpm-workspace-menu-item-id">{{ p.id }}</span>
                }
              </li>
            }
          }
        </ul>
      </div>
    </nz-dropdown-menu>

    <nz-dropdown-menu #moreMenu="nzDropdownMenu">
      <ul nz-menu>
        <li nz-menu-item [nzDisabled]="!projectId()" (click)="openRenameProject()">
          <span nz-icon nzType="edit"></span>
          重命名
        </li>
        <li nz-menu-item (click)="openImportAsNewProject()">
          <span nz-icon nzType="import"></span>
          从模板新建项目
        </li>
        <li nz-menu-divider></li>
        <li nz-menu-item nzDanger [nzDisabled]="!projectId()" (click)="confirmDeleteProject()">
          <span nz-icon nzType="delete"></span>
          删除项目
        </li>
      </ul>
    </nz-dropdown-menu>
  `,
  imports: [
    FormsModule,
    NzButtonModule,
    NzDropdownModule,
    NzEmptyModule,
    NzIconModule,
    NzInputModule,
    NzMenuModule,
    NzSpinModule,
    NzTagModule,
    NzTooltipModule
  ],
  styleUrls: ['./bpm-project-workspace.component.less'],
  encapsulation: ViewEncapsulation.None
})
export class BpmProjectWorkspaceComponent implements OnInit {
  /** 与路由 query projectId 同步 */
  readonly projectId = input<string | null>(null);

  /** 当前选中项目的 id / 显示名，供流程区使用 */
  readonly activeProjectChange = output<BpmProjectOption | null>();

  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly workspace = inject(BpmWorkspaceService);
  private readonly http = inject(BaseHttpService);
  private readonly modalWrap = inject(NzModalWrapService);
  private readonly message = inject(NzMessageService);

  readonly projectSearch = signal('');
  readonly projects = signal<BpmProjectOption[]>([]);
  readonly projectsLoading = signal(false);

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
      if (id) {
        this.workspace.setLastProjectId(String(id));
      }
      this.emitActiveProject();
    });
  }

  ngOnInit(): void {
    void this.loadProjects();
  }

  onProjectMenuVisible(visible: boolean): void {
    if (!visible) {
      this.projectSearch.set('');
    }
  }

  private emitActiveProject(): void {
    const id = this.projectId();
    if (!id) {
      this.activeProjectChange.emit(null);
      return;
    }
    const p = this.projects().find(x => x.id === id);
    this.activeProjectChange.emit(p ?? { id, name: id });
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
      this.emitActiveProject();
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
      this.navigateProjectId(next);
    }
  }

  selectProject(id: string): void {
    this.navigateProjectId(id);
  }

  private navigateProjectId(id: string): void {
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
            this.navigateProjectId(id);
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
            this.emitActiveProject();
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
    this.emitActiveProject();
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
            void this.loadProjects().then(() => this.navigateProjectId(res.projectId!));
          }
        });
      }
    });
  }
}
