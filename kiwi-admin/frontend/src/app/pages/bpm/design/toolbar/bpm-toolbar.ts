import { Component, inject, input } from '@angular/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzIconModule } from 'ng-zorro-antd/icon';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import { NzTooltipDirective } from "ng-zorro-antd/tooltip";
import { BpmEditorToken } from '../editor/bpm-editor';
@Component({
    selector: 'bpm-toolbar',
    templateUrl: './bpm-toolbar.html',
    styleUrls: ['bpm-toolbar.css'],
    standalone: true,
    imports: [
        NzButtonModule,
        NzIconModule,
        NzTooltipDirective
    ]
})
export class BpmToolbar {

    bpmnEditor = inject(BpmEditorToken);
    bpmnModeler = input.required<BpmnModeler>();


    actions = [
        // {
        //     key: 'mouse', label: '鼠标', icon: 'bpmn-shubiaojiantou', onClick: () => {
        //         let lassoTool: any = this.bpmnModeler().get("lassoTool")
        //         lassoTool.activateSelection({ x: 100, y: 100 });
        //     }
        // },
        // {
        //     key: 'line', label: '连接线', icon: 'bpmn-bottom-arrow-solid', onClick: () => {
        //         let tool: any = this.bpmnModeler().get("globalConnect")
        //         tool.toggle();
        //     }
        // },
        // {
        //     key: 'hand', label: '手型工具', icon: 'bpmn-hand', onClick: () => {
        //         let handTool: any = this.bpmnModeler().get("handTool")
        //         handTool.activateHand();
        //     }
        // },
        {
            key: 'select', label: '选择', icon: 'bpmn-kuangxuan', onClick: (event: any) => {
                let lassoTool: any = this.bpmnModeler().get("lassoTool")
                console.log(event);
                lassoTool.activateSelection(event);
            }
        },
        { key: 'undo', label: '撤销', icon: 'bpmn-undo', onClick: () => this.onUndo() },
        { key: 'redo', label: '重做', icon: 'bpmn-redo', onClick: () => this.onRedo() },
        { key: 'save', label: '保存', icon: 'bpmn-save', onClick: () => this.onSave() },
        {
            key: 'xml', label: 'XML', icon: 'bpmn-xml', onClick: () => {
                this.bpmnModeler().saveXML({ format: true }).then(({ xml }) => {
                    console.log(xml);
                });
            }
        },
        {
            key: 'deploy', label: '部署', icon: 'bpmn-deploy', onClick: () => {
                this.bpmnEditor.deploy()
            }
        },
        {
            key: 'start', label: '启动', icon: 'bpmn-start', onClick: () => {
                this.bpmnEditor.start()
            }
        }


    ]

    onSelect() {
    }


    onSave(): void {
        // Save BPM diagram logic
        this.bpmnEditor.save();
    }

    onUndo(): void {
        (<any>this.bpmnModeler().get('commandStack')).undo();
        // Undo logic
    }

    onRedo(): void {
        (<any>this.bpmnModeler().get('commandStack')).redo();
        // Redo logic
    }

    onZoomIn(): void {
        // Zoom in logic
    }

    onZoomOut(): void {
        // Zoom out logic
    }

    onZoomReset(): void {
        // Reset zoom logic
    }

    onExport(): void {
        // Export diagram logic
    }

    onValidate(): void {
        // Validate diagram logic
    }
}