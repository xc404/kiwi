import { Injectable, isDevMode } from '@angular/core';

import type {
  BpmDesignerToolbarCommand,
  BpmDesignerToolbarContext,
  BpmDesignerToolbarGroup,
} from './bpm-designer-toolbar.types';

const GROUP_ORDER: BpmDesignerToolbarGroup[] = ['tools', 'edit', 'view', 'file'];

@Injectable({ providedIn: 'root' })
export class BpmDesignerToolbarService {
  private readonly commands = new Map<string, BpmDesignerToolbarCommand>();

  register(cmd: BpmDesignerToolbarCommand): void {
    const id = cmd.id?.trim();
    if (!id) {
      return;
    }
    if (this.commands.has(id) && isDevMode()) {
      // eslint-disable-next-line no-console -- 开发期诊断重复注册
      console.warn(`[BpmDesignerToolbarService] 覆盖已注册命令: ${id}`);
    }
    this.commands.set(id, { ...cmd, id });
  }

  registerAll(cmds: readonly BpmDesignerToolbarCommand[]): void {
    for (const cmd of cmds) {
      this.register(cmd);
    }
  }

  has(id: string): boolean {
    return this.commands.has(id.trim());
  }

  run(id: string, ctx: BpmDesignerToolbarContext, options?: Record<string, unknown>): void {
    const cmd = this.commands.get(id.trim());
    if (!cmd) {
      throw new Error(`不支持的工具栏命令: ${id}`);
    }
    const result = cmd.run(ctx, options);
    if (result instanceof Promise) {
      void result;
    }
  }

  listUiCommands(): BpmDesignerToolbarCommand[] {
    const list = [...this.commands.values()].filter((c) => c.showInToolbar !== false);
    return list.sort((a, b) => GROUP_ORDER.indexOf(a.group) - GROUP_ORDER.indexOf(b.group));
  }

  listAiCommandIds(): string[] {
    return [...this.commands.values()]
      .filter((c) => c.aiExposed !== false)
      .map((c) => c.id);
  }
}
