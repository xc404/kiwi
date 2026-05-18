import { ProcessInstanceService } from './../service/process-instance.service';
import { CommonModule } from '@angular/common';
import {
    Component,
    computed,
    effect,
    inject,
    input,
    OnInit,
    signal
} from '@angular/core';
import { Element } from 'bpmn-js/lib/model/Types';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { ComponentDescription } from '../../flow-elements/component-provider';
import { ComponentService } from '../../flow-elements/component-service';
import {
    BpmProcessInstanceDto,
    CamundaHistoricVariableInstance,
} from '../service/process-instance.service';
import { CustomInputsPanel } from './custom-inputs/custom-inputs-panel';
import { CustomOutputsPanel } from './custom-outputs/custom-outputs-panel';
import { PanelHeader } from './panel-header';
import { ProcessInstanceVariablesListComponent } from './process-instance-variables-list.component';
import { PropertyGroupEdit } from './property-group-edit';
import { PropertyGroupReadonly } from './property-group-readonly';
import { CompositePropertyProvider,  PropertyTab } from './property-provider';
import Viewer from 'bpmn-js/lib/Viewer';

/** Modeler 与只读 Viewer 均基于 diagram-js，事件与 get('canvas') 一致 */
export type BpmnDiagramHost = BpmnModeler | NavigatedViewer;

/** 升序：较早的 createTime 在前；无 createTime 或无法解析的排在末尾 */
function variableCreateTimeMs(v: CamundaHistoricVariableInstance): number {
    const raw = v.createTime;
    if (raw == null || raw === '') {
        return Number.MAX_SAFE_INTEGER;
    }
    const t = Date.parse(String(raw));
    return Number.isNaN(t) ? Number.MAX_SAFE_INTEGER : t;
}

@Component({
    selector: 'bpm-properties-panel',
    templateUrl: 'properties-panel.html',
    styleUrls: ['properties-panel.css'],
    imports: [CommonModule, NzTabsModule, PanelHeader, NzCollapseModule, PropertyGroupEdit, PropertyGroupReadonly, CustomInputsPanel, CustomOutputsPanel, ProcessInstanceVariablesListComponent],
    standalone: true,
})
export class BpmPropertiesPanel implements OnInit {
    private readonly processInstanceService = inject(ProcessInstanceService);

    bpmnModeler = input.required<Viewer>();
    /** 当前编辑中的流程定义 id（流程选择器排除自调用） */
    currentProcessId = input<string | null | undefined>(null);
    /** 当前流程所属项目（缩小可选子流程列表） */
    projectId = input<string | null | undefined>(null);
    /** 流程实例查看：只展示运行时变量，不加载建模属性 */
    viewMode = input(false);
    processInstance = input<BpmProcessInstanceDto>(undefined as unknown as BpmProcessInstanceDto);

    /**
     * 属性折叠区（`.properties-panel-collapse-scroll`）的 max-height。
     * 可用 `calc(100dvh - …px)` 按布局预留；设计器/查看器可按壳层高度单独传入。
     */
    usedHeight = input<number>();
    collapseScrollMaxHeight = computed(() => {
        var height = this.usedHeight() ?? 0;
        height = height + 103;
        return `calc(100dvh - ${height}px)`;
    });

    element = signal<Element>(undefined as unknown as Element);
    tabs = signal<PropertyTab[]>([]);
    propertyProvider = inject(CompositePropertyProvider);

    private readonly instanceVariables = signal<any[]>([]);
    processInstanceVariables = signal<any[]>([]);
    rootSelected = signal(true);

    constructor() {
        effect(() => {
            this.processInstance();
            this.loadVariables();
        });
    }


    ngOnInit(): void {
        this.subscribeElementChange();
    }

    subscribeElementChange() {
        this.bpmnModeler().on('selection.changed', (e: { newSelection: unknown[] }) => {
            let element = e.newSelection[e.newSelection.length - 1];
            if (!element) {
                const canvas: { getRootElement: () => Element } = this.bpmnModeler().get('canvas');
                element = canvas.getRootElement();
                this.rootSelected.set(true);
            }else {
                this.rootSelected.set(false);
            }
            this.element.set(element as Element);
            if (this.element()) {
                this.loadElement();
                
            }
        });
    }

    loadElement() {
        const tabs = this.propertyProvider.getProperties(this.element());
        this.tabs.set(tabs);
    }


    isOutputTab(tab: PropertyTab): boolean {
        return (tab.name ?? '') === '输出';
    }

    isInputTab(tab: PropertyTab): boolean {
        return (tab.name ?? '') === '输入';
    }

    showCustomOutputs = computed(() => {
        const type = this.element()?.type;
        return type === 'bpmn:ServiceTask' || type === 'bpmn:CallActivity';
    });

    showCustomInputs = computed(() => this.element()?.type === 'bpmn:CallActivity');

    /** 存在流程实例 id 时展示流程变量折叠区与「流程变量」Tab */
    isProcessInstanceContext = computed(() => {
        return this.rootSelected() && this.processInstance();
    });


    loadVariables() {
        const id = this.processInstance()?.id;
        if (!id) {
            this.instanceVariables.set([]);
            this.processInstanceVariables.set([]);
            return;
        }
        this.processInstanceService.getProcessInstanceVariables(id).subscribe({
            next: (variables) => {
                const sorted = [...variables].sort((a, b) => {
                    const dt =
                        variableCreateTimeMs(a) - variableCreateTimeMs(b);
                    if (dt !== 0) {
                        return dt;
                    }
                    const na = a.name ?? '';
                    const nb = b.name ?? '';
                    return na.localeCompare(nb);
                });
                this.instanceVariables.set(sorted);
                const processScoped = sorted.filter((v: CamundaHistoricVariableInstance) => {
                    const aid = v.activityInstanceId;
                    return aid == null || aid === '' || aid === id;
                });
                this.processInstanceVariables.set(processScoped);
            },

            error: (err) => {
                console.error('Failed to load process variables', err);
            },
        });
    }

    activityVariables = computed(() => {
        if (!this.viewMode() || !this.processInstance()) {
            return [];
        }
        const elementId = this.element()?.id;
        if (!elementId) {
            return [];
        }

        const activityScoped = this.instanceVariables().filter((v) => {
            const raw = v.activityInstanceId;
            if (typeof raw !== 'string' || raw === '') {
                return false;
            }
            const prefix = raw.split(':')[0];
            return prefix === elementId;
        });

        const activityByName = new Map<string, any>();
        for (const v of activityScoped) {
            const name = v?.name;
            if (typeof name !== 'string' || name === '') {
                continue;
            }
            activityByName.set(name, v);
        }
        const activityDeduped = Array.from(activityByName.values());
        const activityNames = new Set(activityByName.keys());

        const processByName = new Map<string, any>();
        for (const v of this.processInstanceVariables()) {
            const name = v?.name;
            if (typeof name !== 'string' || name === '') {
                continue;
            }
            processByName.set(name, v);
        }
        const processOnly = Array.from(processByName.values()).filter((v) =>
            !activityNames.has(v.name as string),
        );

        return [...activityDeduped, ...processOnly];
    });
}
