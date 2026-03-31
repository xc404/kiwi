import { inject, Injectable } from '@angular/core';

import { WindowService } from '@core/services/common/window.service';

/** localStorage 键：上次进入的 BPM 项目（工作区）ID */
const LAST_WORKSPACE_PROJECT_ID_KEY = 'kiwi.bpm.lastWorkspaceProjectId';

@Injectable({ providedIn: 'root' })
export class BpmWorkspaceService {
  private readonly window = inject(WindowService);

  setLastProjectId(id: string): void {
    if (id) {
      this.window.setStorage(LAST_WORKSPACE_PROJECT_ID_KEY, id);
    }
  }

  getLastProjectId(): string | null {
    return this.window.getStorage(LAST_WORKSPACE_PROJECT_ID_KEY);
  }

  clearLastProjectId(): void {
    this.window.removeStorage(LAST_WORKSPACE_PROJECT_ID_KEY);
  }
}
