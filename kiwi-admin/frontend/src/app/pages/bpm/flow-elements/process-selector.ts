import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSelectModule } from 'ng-zorro-antd/select';

import { ProcessDesignService } from '../design/service/process-design.service';
import type { BpmProcess } from '../types/bpm-process';

function isProcessDeployed(p: BpmProcess): boolean {
  return p.deployedVersion != null && p.deployedVersion > 0;
}

@Component({
  selector: 'bpm-process-selector',
  template: ` <nz-select nzAllowClear nzShowSearch style="width: 100%" [formControl]="control()" [nzOptions]="options()" [nzPlaceHolder]="'选择流程'"></nz-select> `,
  imports: [NzSelectModule, FormsModule, ReactiveFormsModule],
  standalone: true
})
export class ProcessSelector {
  private readonly processDesignService = inject(ProcessDesignService);
  private readonly modalWrap = inject(NzModalWrapService);
  private readonly message = inject(NzMessageService);

  control = input<any>();
  excludeProcessId = input<string | null | undefined>(null);
  projectId = input<string | null | undefined>(null);

  private readonly processes = signal<BpmProcess[]>([]);
  private previousValue: string | null = null;
  private suppressValueChange = false;

  constructor() {
    effect(() => {
      const projectId = this.projectId();
      this.processDesignService.listVisibleProcesses(projectId ? { projectId } : undefined).subscribe(list => {
        this.processes.set(list);
        this.processDesignService.cacheProcessDisplayNames(list);
      });
    });

    effect(onCleanup => {
      const ctrl = this.control();
      if (!ctrl) {
        return;
      }
      this.previousValue = normalizeProcessId(ctrl.value);
      const sub = ctrl.valueChanges.subscribe((value: unknown) => {
        if (this.suppressValueChange) {
          this.suppressValueChange = false;
          this.previousValue = normalizeProcessId(value);
          return;
        }
        const processId = normalizeProcessId(value);
        if (!processId) {
          this.previousValue = processId;
          return;
        }
        const process = this.processes().find(p => p.id === processId);
        if (!process || isProcessDeployed(process)) {
          this.previousValue = processId;
          return;
        }
        this.confirmDeployUndeployedProcess(process);
      });
      onCleanup(() => sub.unsubscribe());
    });
  }

  options = computed(() => {
    const exclude = (this.excludeProcessId() ?? '').trim();
    return this.processes()
      .filter(p => {
        const id = (p.id ?? '').trim();
        return id.length > 0 && id !== exclude;
      })
      .map(p => {
        const id = String(p.id);
        const base = p.name?.trim() ? `${p.name} (${id})` : id;
        const label = isProcessDeployed(p) ? base : `${base} [未部署]`;
        return { label, value: p.id };
      });
  });

  private confirmDeployUndeployedProcess(process: BpmProcess): void {
    const id = (process.id ?? '').trim();
    if (!id) {
      return;
    }
    const displayName = process.name?.trim() || id;
    this.modalWrap.confirm({
      nzTitle: '流程未部署',
      nzContent: `流程「${displayName}」尚未部署到 Camunda 引擎，Call Activity 运行时无法调用。是否立即部署？`,
      nzOkText: '部署',
      nzCancelText: '取消',
      nzOnOk: () =>
        firstValueFrom(this.processDesignService.deployProcess(id))
          .then(updated => {
            this.processes.update(list => list.map(p => (p.id === id ? updated : p)));
            this.processDesignService.cacheProcessDisplayNames([updated]);
            this.message.success(`流程「${displayName}」部署成功`);
            this.previousValue = id;
          })
          .catch(() => {
            this.message.error(`流程「${displayName}」部署失败`);
            this.revertSelection();
          }),
      nzOnCancel: () => this.revertSelection()
    });
  }

  private revertSelection(): void {
    const ctrl = this.control();
    if (!ctrl) {
      return;
    }
    this.suppressValueChange = true;
    ctrl.setValue(this.previousValue, { emitEvent: true });
  }
}

function normalizeProcessId(value: unknown): string | null {
  if (value == null) {
    return null;
  }
  const trimmed = String(value).trim();
  return trimmed.length > 0 ? trimmed : null;
}
