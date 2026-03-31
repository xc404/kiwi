import { Component, computed, ElementRef, inject, OnInit, signal, TemplateRef, ViewChild } from '@angular/core';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css';
import 'bpmn-js/dist/assets/diagram-js.css'; // 左边工具栏以及编辑节点的样式
import BpmnModeler from 'bpmn-js/lib/Modeler';
import Create from 'diagram-js/lib/features/create/Create';
// import 'bpmn-js-properties-panel/dist/assets/bpmn-js-properties-panel.css'
// import "bpmn-js-properties-panel/dist/assets/bpmn-js-properties-panel.css"
// import "node_modules/bpmn-js-properties-panel/dist/assets/bpmn-js-properties-panel.css"
// import paletteModule from '../palette';
// import propertiesProviderModule from 'bpmn-js-properties-panel/lib/provider/camunda.json'
// import camundaModdleDescriptor from 'camunda-bpmn-moddle/resources/camunda'
// 而这个引入的是右侧属性栏里的内容
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatExpansionModule } from "@angular/material/expansion";
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute } from '@angular/router';
import BpmnFactory from 'bpmn-js/lib/features/modeling/BpmnFactory';
import ElementFactory from 'bpmn-js/lib/features/modeling/ElementFactory';
import { NzIconService } from 'ng-zorro-antd/icon';
import { NzLayoutComponent, NzLayoutModule } from "ng-zorro-antd/layout";
import { ElementModel } from '../extension/element-model';
import { BpmPallete } from "../palette/pallete";
import { BpmPropertiesPanel } from '../property-panel/properties-panel';
import { ProcessDesignService } from '../service/process-degisn.service';
import { BpmToolbar } from "../toolbar/bpm-toolbar";
import kiwiDescriptor from '../../component/kiwi.json';

export abstract class BpmEditorToken {
  abstract deploy(): void;
  abstract start(): void;
  abstract save(): void;

  abstract clearSelection(): void;

  bpmnModeler!: BpmnModeler
}


@Component({
  selector: 'bpm-editor',
  templateUrl: './bpm-editor.html',
  styleUrl: './bpm-editor.scss',
  providers: [
    {
      provide: BpmEditorToken, useExisting: BpmEditor,
    }
  ],
  imports: [BpmPropertiesPanel, MatIconModule, MatExpansionModule,

    MatDialogModule, MatFormFieldModule, FormsModule,
    BpmPallete, NzLayoutComponent, NzLayoutModule, BpmToolbar],
  standalone: true,
})
export class BpmEditor implements OnInit, BpmEditorToken {


  acvitedRoute = inject(ActivatedRoute);

  http = inject(HttpClient);
  processDefinitionService = inject(ProcessDesignService)
  matDialog = inject(MatDialog);

  elementModel = inject(ElementModel);


  bpmnModeler!: BpmnModeler<null>;

  bpmnId = signal<string>('');
  bpmProcess = signal<any>(null);

  iconService = inject(NzIconService)

  @ViewChild('#canvas') canvas: ElementRef | undefined;
  protected readonly title = signal('bpm-frontend');

  @ViewChild('processNameDialog')
  processNameDialog!: TemplateRef<any>;

  processName: any;
  autoSave = false;

  updatedAt = computed(() => {
    return this.bpmProcess()?.updatedAt;
  });

  delopyAt = computed(() => {
    return this.bpmProcess()?.deployedAt;
  });
  bpmnFactory!: BpmnFactory;
  create!: Create;
  elementFactory!: ElementFactory;

  depolyVersionBehind = computed(() => {
    if (!this.bpmProcess()?.version) {
      return false;
    }
    if (!this.bpmProcess()?.deployedVersion) {
      return true;
    }
    return this.bpmProcess()?.version > this.bpmProcess()?.deployedVersion;
  });
  stackIdx = undefined;
  commandStack: any;

  constructor() {
    this.acvitedRoute.params.subscribe(params => {
      this.bpmnId.set(params['id']);
    });
  }

  ngOnInit(): void {

    this.bpmnModeler = new BpmnModeler({
      container: ".canvas",
      additionalModules: [
        {
          http: ['value', this.http],
        },
      ],
      moddleExtensions: {
        moddleProvider: this.elementModel.getModdleExtension(),
        componentProvider: kiwiDescriptor
      }
    })
    this.bpmnFactory = this.bpmnModeler.get('bpmnFactory');
    this.create = this.bpmnModeler.get('create');
    this.elementFactory = this.bpmnModeler.get('elementFactory');
    this.commandStack = this.bpmnModeler.get('commandStack');

    this.loadDefinition();


  }

  dirty() {

    return this.commandStack._stackIdx !== this.stackIdx;
  }

  // 下载为SVG格式,done是个函数，调用的时候传入的
  saveSVG() {
    // 把传入的done再传给bpmn原型的saveSVG函数调用
    this.bpmnModeler.saveSVG();
  }
  save() {
    let stackIdx = this.commandStack._stackIdx;
    if (!this.dirty()) {
      return Promise.resolve(this.bpmProcess());
    }
    return new Promise((resolve) => {
      this.bpmnModeler.saveXML({ format: true }).then((bpmn: any) => {
        this.processDefinitionService.updateProcess(this.bpmProcess().id, {
          bpmnXml: bpmn.xml
        }).subscribe(
          (data: any) => {
            this.bpmProcess.set(data)
            this.stackIdx = stackIdx;
            resolve(data)
          }
        );
      });
    });

  }

  clearSelection() {
    let selection: any = this.bpmnModeler.get('selection');
    selection.select(null);
  }
  saveAsDefinition() {

    this.matDialog.open(this.processNameDialog).afterClosed().subscribe(result => {
      if (result) {
        this.bpmnModeler.saveXML({ format: true }).then((bpmn: any) => {
          this.processDefinitionService.saveAsProcess(this.bpmProcess().id, this.processName, bpmn.xml).subscribe(
            (data: any) => {
              this.bpmProcess.set(data)
            }
          );
        });
      }
    });
  }

  deploy() {
    return this.save().then(() => {
      if (!this.depolyVersionBehind()) {
        return Promise.resolve(this.bpmProcess());
      }
      return new Promise((resolve) => {
        this.processDefinitionService.deployProcess(this.bpmProcess().id).subscribe(
          (data: any) => {
            this.bpmProcess.set(data)
            resolve(data)
          }
        );
      });
    });
  };



  loadDefinition() {
    this.processDefinitionService.getProcessById(this.bpmnId()).subscribe(
      (data: any) => {
        this.bpmProcess.set(data);
        this.bpmnModeler.importXML(this.bpmProcess().bpmnXml);

        // this.bpmnModeler.on('elements.changed', (e: any) => {
        //   this.stackIdx = this.bpmnModeler.get('commandStack')._stackIdx;
        // })
      }
    );
  }

  start() {
    return this.deploy().then(() => {
      return new Promise((resolve) => {
        this.processDefinitionService.startProcess(this.bpmnId()).subscribe(
          (data: any) => {
            console.log(data);
            resolve(data);
          }
        );
      });
    });
  }

  toSaveAsComponentPage() {
    this.bpmnModeler.saveXML({ format: true }).then((bpmn: any) => {
      console.log(bpmn.xml);
    });
  }

  onDrop(event: any) {
    console.log('Drop event:', event);
    // this.createElement({} as ComponentDescription, event.event);
  }



}

