import { CommonModule } from "@angular/common";
import { Component, computed, inject, input, OnInit, signal } from "@angular/core";
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTabsModule } from '@angular/material/tabs';
import { Element } from "bpmn-js/lib/model/Types";
import BpmnModeler from 'bpmn-js/lib/Modeler';
import * as ModelUtil from 'bpmn-js/lib/util/ModelUtil';
import { PROPERTY_PROVIDER, PropertyTab } from "./property-provider";
import { NzCollapseModule } from "ng-zorro-antd/collapse";
import { NzTabsModule } from "ng-zorro-antd/tabs";
import { PanelHeader } from "./panel-header";
import { PropertyGroup } from "./property-group";
import { ComponentDescription } from "../../component/component-provider";
@Component({
    selector: 'bpm-properties-panel',
    templateUrl: "properties-panel.html",
    styleUrls: ["properties-panel.css"],
    imports: [CommonModule, MatTabsModule, MatExpansionModule, NzTabsModule, PanelHeader, NzCollapseModule, PropertyGroup],
    standalone: true
})
export class BpmPropertiesPanel implements OnInit {

    bpmnModeler = input.required<BpmnModeler>();
    element = signal<Element>(undefined as any as Element);
    tabs = signal<PropertyTab[]>([]);
    component = signal<ComponentDescription>(undefined as any as ComponentDescription);
    propertyProvider = inject(PROPERTY_PROVIDER);
    constructor() {
        // elementModel.inject(this);
    }

    ngOnInit(): void {
        this.loadModuler();
        // this.elementModel.inject(this.bpmnModeler);
    }


    loadModuler() {
        this.bpmnModeler().on('selection.changed', (e: { newSelection: any[] }) => {
            let element = e.newSelection[e.newSelection.length - 1]; //bpmn-js7+的版本，元素可多选。这里默认为多选最后点击的元素。
            // if (this.element) {
            //     this.cd.detectChanges();
            // }
            if (!element) {
                let canvas: any = this.bpmnModeler().get('canvas');
                element = canvas.getRootElement();
            }
            this.element.set(element);
            if (this.element()) {
                this.loadElement();
            }
        })
    }

    // 更新元素属性
    // updateProperties(properties: any) {
    //     const modeling: any = this.bpmnModeler.get('modeling');
    //     modeling.updateProperties(this.element, properties);
    // }

    loadElement() {
        // if(type ==  'bpmn:ServiceTask' ){
        //     let componentId = this.element.businessObject.get("componentId");
        //     let component = this.bpmComponentProvider.getComponent(componentId);

        // }
        let tabs = this.propertyProvider.getProperties(this.element());
        // if (ModelUtil.is(this.element(), "bpmn:ServiceTask")) {
        //     this.loadComponent();
        // }
        this.tabs.set(tabs);
    }


    // private loadComponent() {
    //     // let componentId = this.elementModel.getValue(this.element(), "", "componentId");
    //     // if (componentId) {
    //     //     let component = this.bpmComponentProvider.getComponent(componentId);
    //     //     this.component.set(component);
    //     //     this.componentService.initComponent(this.element(), component);
    //     // } else {
    //     //     this.component.set(null as any);
    //     // }
    // }


    isServiceTask = computed(() => {
        return this.element().type === 'bpmn:ServiceTask';
    })
}
