import {
  Component,
  computed,
  effect,
  inject,
  OnInit,
  signal,
  viewChild,
  ViewEncapsulation,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { ColumnToken } from '@app/shared/components/table/column';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzDropdownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMenuModule } from 'ng-zorro-antd/menu';
import { finalize } from 'rxjs/operators';

import { BpmWorkspaceService } from './bpm-workspace.service';

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
            <i nz-icon nzType="folder-open" nzTheme="outline"></i>
          </span>
          <div class="bpm-workspace-toolbar-text">
            <div class="bpm-workspace-toolbar-title">项目工作区</div>
            <div class="bpm-workspace-toolbar-desc">切换项目以查看与管理该项目下的流程</div>
          </div>
        </div>
        <div class="bpm-workspace-toolbar-action">
          <span class="bpm-workspace-toolbar-label">当前项目</span>

           <a #projectTrigger nz-dropdown [nzDropdownMenu]="projectMenu"
           nzOverlayClassName="bpm-workspace-project-menu-overlay"
           class="bpm-workspace-trigger">
      <span class="bpm-workspace-trigger-label">{{ currentProjectLabel() }}</span>
      <nz-icon nzType="down" />
    </a>
        </div>
      </div>

      <nz-dropdown-menu #projectMenu>
        <div class="bpm-workspace-dropdown" (click)="$event.stopPropagation()">
          <ul nz-menu class="bpm-workspace-menu">
            @if (!filteredProjects().length) {
              <li nz-menu-item nzDisabled class="bpm-workspace-menu-empty">
                {{ projects().length ? '无匹配项目' : '暂无项目' }}
              </li>
            } @else {
              @for (p of filteredProjects(); track p.id) {
                <li
                  nz-menu-item
                  [nzSelected]="p.id === projectId()"
                  (click)="setProjectId(p.id)"
                >
                  {{ p.name || p.id }}
                </li>
              }
            }
          </ul>
        </div>
      </nz-dropdown-menu>

      <crud-page [pageConfig]="pageConfig"> </crud-page>
    </section>
  `,
  imports: [
    PageHeaderComponent,
    CrudPage,
    FormsModule,
    NzButtonModule,
    NzDropdownModule,
    NzIconModule,
    NzInputModule,
    NzMenuModule,
  ],
  styleUrls: ['./bpm-project-process.less'],
  encapsulation: ViewEncapsulation.None,
})
export class BpmProjectProcess implements OnInit {
  private readonly activatedRoute = inject(ActivatedRoute);
  router = inject(Router);
  private readonly workspace = inject(BpmWorkspaceService);
  private readonly http = inject(BaseHttpService);

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
    return list.filter(
      p => (p.name || '').toLowerCase().includes(q) || p.id.toLowerCase().includes(q)
    );
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
      .get<{ content?: { id?: string; name?: string }[] }>('/bpm/project', { page: 0, size: 500 })
      .pipe(finalize(() => this.projectsLoading.set(false)))
      .subscribe({
        next: res => {
          const rows = res?.content ?? [];
          this.projects.set(
            rows.map(r => ({
              id: String(r.id ?? ''),
              name: r.name ?? '',
            }))
          );
        },
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
      queryParamsHandling: 'merge',
    });
  }


  pageConfig: PageConfig = {
    title: '工作流',
    crud: '/bpm/process',
    columnActions: [
      {
        icon: 'right-square',
        tooltip: '流程管理',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record?.id) {
            window.open(`http://localhost:4201/#/bpm/design/${record.id}`);
          }
        },
      },
      {
        icon: 'cluster',
        tooltip: '流程实例',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record?.id) {
            this.router.navigate(['/default/bpm/processinstances'], {
              queryParams: { processDefinitionKey: record.id },
            });
          }
        },
      },
    ],
    fields: [
      { name: '名称', dataIndex: 'name' },
      {
        name: '项目ID',
        dataIndex: 'projectId',
        column: 'disabled',
        edit: {
          create: 'hidden',
          update: 'hidden',
        },
      },
    ],
  };
}
