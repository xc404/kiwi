import { Injectable } from '@angular/core';

/**
 * 启动流程变量在 localStorage 中的读写（按流程 ID 隔离）。
 */
@Injectable({
  providedIn: 'root',
})
export class BpmStartVariablesService {
  private readonly prefix = 'kiwi.bpm.editor.startVariables.v1';

  storageKey(processId: string): string {
    return `${this.prefix}:${processId}`;
  }

  load(processId: string): Record<string, unknown> | undefined {
    if (!processId || typeof localStorage === 'undefined') {
      return undefined;
    }
    try {
      const raw = localStorage.getItem(this.storageKey(processId));
      if (raw == null || raw === '') {
        return undefined;
      }
      const parsed = JSON.parse(raw) as unknown;
      if (parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed as Record<string, unknown>;
      }
      return undefined;
    } catch {
      return undefined;
    }
  }

  persist(processId: string, variables: Record<string, unknown>): void {
    if (!processId || typeof localStorage === 'undefined') {
      return;
    }
    try {
      localStorage.setItem(this.storageKey(processId), JSON.stringify(variables));
    } catch {
      /* quota or private mode */
    }
  }

  /**
   * 解析启动变量：不传则从 localStorage 读取该流程上次变量；字符串须为 JSON 对象。
   */
  resolve(processId: string, input?: Record<string, unknown> | string): Record<string, unknown> {
    if (input === undefined) {
      return this.load(processId) ?? {};
    }
    if (typeof input === 'string') {
      const trimmed = input.trim();
      if (trimmed === '') {
        return {};
      }
      const parsed = JSON.parse(trimmed) as unknown;
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        throw new Error('INVALID_SHAPE');
      }
      return parsed as Record<string, unknown>;
    }
    if (Array.isArray(input)) {
      throw new Error('INVALID_SHAPE');
    }
    return input;
  }
}
