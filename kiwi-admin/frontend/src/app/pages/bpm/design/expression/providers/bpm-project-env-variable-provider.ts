import { inject, Injectable } from '@angular/core';

import { BpmDesignerContextService } from '../../bpm-designer-context.service';
import { BpmProjectEnvCatalogService } from '../../env/bpm-project-env-catalog.service';
import type { ProjectEnvVarMeta } from '../../env/project-env-var-meta';
import { ExpressionVariableProvider, ExpressionVariableProviderContext } from '../expression-variable-provider';

@Injectable({ providedIn: 'root' })
export class BpmProjectEnvVariableProvider implements ExpressionVariableProvider {
  readonly id = 'bpm-project-env';

  private readonly designerContext = inject(BpmDesignerContextService);
  private readonly catalogService = inject(BpmProjectEnvCatalogService);

  provide(context: ExpressionVariableProviderContext): void {
    const projectId = this.designerContext.projectId();
    if (!projectId) {
      return;
    }
    for (const meta of this.catalogService.getCatalog(projectId).values()) {
      context.addVariable({
        key: meta.key,
        name: this.displayName(meta),
        kind: 'projectEnv'
      });
    }
  }

  private displayName(meta: ProjectEnvVarMeta): string | undefined {
    const desc = meta.description?.trim();
    if (meta.encrypted) {
      return desc ? `${desc}（加密）` : '（加密）';
    }
    return desc || undefined;
  }
}
