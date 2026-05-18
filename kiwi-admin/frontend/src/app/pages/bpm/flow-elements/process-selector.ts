import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { ProcessDesignService } from '../design/service/process-design.service';
import type { BpmProcess } from '../types/bpm-process';

@Component({
    selector: 'bpm-process-selector',
    template: `
        <nz-select
            style="width: 100%"
            nzShowSearch
            nzAllowClear
            [nzPlaceHolder]="'选择流程'"
            [nzOptions]="options()"
            [formControl]="control()"
        ></nz-select>
    `,
    imports: [NzSelectModule, FormsModule, ReactiveFormsModule],
    standalone: true,
})
export class ProcessSelector {
    private readonly processDesignService = inject(ProcessDesignService);

    control = input<any>();
    excludeProcessId = input<string | null | undefined>(null);
    projectId = input<string | null | undefined>(null);

    private readonly processes = signal<BpmProcess[]>([]);

    constructor() {
        effect(() => {
            const projectId = this.projectId();
            this.processDesignService
                .listVisibleProcesses(projectId ? { projectId } : undefined)
                .subscribe((list) => {
                    this.processes.set(list);
                    this.processDesignService.cacheProcessDisplayNames(list);
                });
        });
    }

    options = computed(() => {
        const exclude = (this.excludeProcessId() ?? '').trim();
        return this.processes()
            .filter((p) => {
                const id = (p.id ?? '').trim();
                return id.length > 0 && id !== exclude;
            })
            .map((p) => ({
                label: p.name?.trim() ? `${p.name} (${p.id})` : String(p.id),
                value: p.id,
            }));
    });
}
