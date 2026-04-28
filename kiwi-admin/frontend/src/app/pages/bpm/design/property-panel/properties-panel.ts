import { ProcessInstanceService } from './../service/process-instance.service';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
    Component,
    computed,
    effect,
    inject,
    input,
    OnInit,
    signal
} from '@angular/core';
import { environment } from '@env/environment';
import { Element } from 'bpmn-js/lib/model/Types';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer';
import { NzCollapseModule } from 'ng-zorro-antd/collapse';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { ComponentDescription } from '../../flow-elements/component-provider';
import { ComponentService } from '../../flow-elements/component-service';
import { ProcessInstance } from '../service/process-instance.service';
import { CustomOutputsPanel } from './custom-outputs/custom-outputs-panel';
import { PanelHeader } from './panel-header';
import { PropertyGroup } from './property-group';
import { PROPERTY_PROVIDER, PropertyTab } from './property-provider';
import Viewer from 'bpmn-js/lib/Viewer';

/** Modeler 与只读 Viewer 均基于 diagram-js，事件与 get('canvas') 一致 */
export type BpmnDiagramHost = BpmnModeler | NavigatedViewer;


@Component({
    selector: 'bpm-properties-panel',
    templateUrl: 'properties-panel.html',
    styleUrls: ['properties-panel.css'],
    imports: [CommonModule, NzTabsModule, PanelHeader, NzCollapseModule, PropertyGroup, CustomOutputsPanel],
    standalone: true,
})
export class BpmPropertiesPanel implements OnInit {
    private readonly http = inject(HttpClient);
    private readonly processInstanceService = inject(ProcessInstanceService);
    private readonly componentService = inject(ComponentService);

    bpmnModeler = input.required<Viewer>();
    /** 流程实例查看：只展示运行时变量，不加载建模属性 */
    viewMode = input(false);
    processInstance = input<ProcessInstance>(undefined as unknown as ProcessInstance);
    activityInstanceIdsByActivityId = input<Record<string, string[]>>({});
    instanceSelectionIsRoot = input(true);
    selectedElementId = input<string | null>(null);

    element = signal<Element>(undefined as unknown as Element);
    tabs = signal<PropertyTab[]>([]);
    component = signal<ComponentDescription>(undefined as unknown as ComponentDescription);
    propertyProvider = inject(PROPERTY_PROVIDER);

    private readonly instanceVariables = signal<any[]>([]);


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

    isServiceTask = computed(() => {
        return this.element().type === 'bpmn:ServiceTask';
    });

    isOutputTab(tab: PropertyTab): boolean {
        return (tab.name ?? '') === '输出';
    }

    showCustomOutputs = computed(() => {
        const type = this.element()?.type;
        return type === 'bpmn:ServiceTask' || type === 'bpmn:CallActivity';
    });

    catalogOutputKeys = computed(() => {
        const component = this.componentService.getComponentForElement(this.element());
        return (component?.outputParameters ?? []).map((p) => p.key);
    });

    loadVariables() {
        if (this.viewMode() && this.processInstance()) {
            this.processInstanceService.getHistoricProcessInstanceVariables(this.processInstance().id).subscribe({
                next: (variables) => {
                    this.instanceVariables.set(variables);
                },
                error: (err) => {
                    console.error('Failed to load process variables', err);
                }
            });
        }
    }

    activityVariables = computed(() => {

        if (!this.viewMode() || !this.processInstance()) {
            return [];
        }

        const activityVariables = this.instanceVariables().filter(v => v.activityId === this.selectedElementId());
        return activityVariables;
    });
}
