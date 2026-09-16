import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';

import { BpmProjectProcessListComponent } from './bpm-project-process-list.component';
import type { BpmProjectOption } from './bpm-project.types';
import { BpmProjectWorkspaceComponent } from './bpm-project-workspace.component';
import { BpmWorkspaceService } from './bpm-workspace.service';

@Component({
  selector: 'app-bpm-project',
  template: `
    <app-page-header></app-page-header>
    <section class="page-content">
      <app-bpm-project-workspace [projectId]="projectId()" (activeProjectChange)="onActiveProjectChange($event)" />

      @if (projectId(); as pid) {
        <app-bpm-project-process-list [projectId]="pid" [projectName]="activeProject()?.name ?? ''" />
      }
    </section>
  `,
  imports: [PageHeaderComponent, BpmProjectWorkspaceComponent, BpmProjectProcessListComponent],
  styleUrls: ['./bpm-project.less']
})
export class BpmProject {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly workspace = inject(BpmWorkspaceService);

  projectId = signal<string | null>(null);
  activeProject = signal<BpmProjectOption | null>(null);

  constructor() {
    this.activatedRoute.queryParamMap.subscribe(qm => {
      let id = qm.get('projectId');
      if (!id) {
        id = this.workspace.getLastProjectId();
      }
      if (id) {
        this.projectId.set(id);
      } else {
        this.projectId.set(null);
      }
    });
  }

  onActiveProjectChange(project: BpmProjectOption | null): void {
    this.activeProject.set(project);
  }
}
