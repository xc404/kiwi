import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
    Component,
    computed,
    effect,
    inject,
    input,
    OnInit,
    signal,
    untracked,
} from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTabsModule } from '@angular/material/tabs';
import { environment } from '@env/environment';
import { Element } from 'bpmn-js/lib/model/Types';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { catchError, map, of } from 'rxjs';
import { ComponentDescription } from '../../component/component-provider';
import { PanelHeader } from './panel-header';
import { PropertyGroup } from './property-group';
import { PROPERTY_PROVIDER, PropertyTab } from './property-provider';

/** Modeler 与只读 Viewer 均基于 diagram-js，事件与 get('canvas') 一致 */
export type BpmnDiagramHost = BpmnModeler | NavigatedViewer;

/** Camunda history variable-instance 列表项 */
interface CamundaVariableInstanceItem {
    name: string | null;
    type?: string | null;
    value?: unknown;
    activityInstanceId?: string | null;
}

@Component({
    selector: 'bpm-properties-panel',
    templateUrl: 'properties-panel.html',
    styleUrls: ['properties-panel.css'],
    imports: [CommonModule, MatTabsModule, MatExpansionModule, NzTabsModule, PanelHeader, NzCollapseModule, PropertyGroup],
    standalone: true,
})
export class BpmPropertiesPanel implements OnInit {
    private readonly http = inject(HttpClient);

    bpmnModeler = input.required<BpmnDiagramHost>();
    /** 流程实例查看：只展示运行时变量，不加载建模属性 */
    instanceMode = input(false);
    processInstance = input <);
    activityInstanceIdsByActivityId = input<Record<string, string[]>>({});
    instanceSelectionIsRoot = input(true);
    selectedElementId = input<string | null>(null);

    element = signal<Element>(undefined as unknown as Element);
    tabs = signal<PropertyTab[]>([]);
    component = signal<ComponentDescription>(undefined as unknown as ComponentDescription);
    propertyProvider = inject(PROPERTY_PROVIDER);

    private readonly instanceVariables = signal<any[]>([]);


    constructor() {
    }

    ngOnInit(): void {
        this.loadModuler();
    }

    loadModuler() {
        this.bpmnModeler().on('selection.changed', (e: { newSelection: unknown[] }) => {
            let element = e.newSelection[e.newSelection.length - 1];
            if (!element) {
                const canvas: { getRootElement: () => Element } = this.bpmnModeler().get('canvas');
                element = canvas.getRootElement();
            }
            this.element.set(element as Element);
            if (this.element()) {
                this.loadElement();
            }
        });
    }

    loadElement() {
        if (this.instanceMode()) {
            this.tabs.set([]);
            return;
        }
        const tabs = this.propertyProvider.getProperties(this.element());
        this.tabs.set(tabs);
    }

    isServiceTask = computed(() => {
        return this.element().type === 'bpmn:ServiceTask';
    });

    private engineRestRoot(): string {
        return `${environment.api.baseUrl}${environment.api.camundaEngineRestPath}`;
    }

    private variableInstanceListToRows(list: CamundaVariableInstanceItem[]): ProcessVariableRow[] {
        const seen = new Set<string>();
        const rows: ProcessVariableRow[] = [];
        for (const row of list) {
            if (!row.name) {
                continue;
            }
            const key = `${row.name}\0${row.activityInstanceId ?? ''}`;
            if (seen.has(key)) {
                continue;
            }
            seen.add(key);
            rows.push({
                name: row.name,
                type: row.type ?? 'Unknown',
                value: row.value,
                scope: row.activityInstanceId ? 'activity' : 'process',
                activityInstanceId: row.activityInstanceId,
            });
        }
        rows.sort((a, b) => {
            const scopeOrder = a.scope === b.scope ? 0 : a.scope === 'process' ? -1 : 1;
            if (scopeOrder !== 0) {
                return scopeOrder;
            }
            return a.name.localeCompare(b.name);
        });
        return rows;
    }
}
