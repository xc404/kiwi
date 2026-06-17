import { inject, Injectable, signal } from '@angular/core';

import { BpmProjectEnvCatalogService } from './env/bpm-project-env-catalog.service';

/** 设计器会话上下文：当前流程所属项目，供表达式变量 Provider 等读取 */
@Injectable({ providedIn: 'root' })
export class BpmDesignerContextService {
  private readonly catalogService = inject(BpmProjectEnvCatalogService);

  readonly projectId = signal<string | null>(null);
  /** 项目环境变量目录加载完成后递增，驱动表达式补全列表刷新 */
  readonly catalogRevision = signal(0);

  setProjectId(projectId: string | null | undefined): void {
    const id = projectId?.trim() || null;
    this.projectId.set(id);
    if (!id) {
      this.catalogRevision.update(n => n + 1);
      return;
    }
    this.catalogService.loadCatalog(id).subscribe(() => {
      this.catalogRevision.update(n => n + 1);
    });
  }
}
