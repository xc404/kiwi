import { Component, inject, InjectionToken, OnInit, signal } from "@angular/core";
import { MatExpansionModule } from "@angular/material/expansion";
import BpmnFactory from "bpmn-js/lib/features/modeling/BpmnFactory";
import ElementFactory from "bpmn-js/lib/features/modeling/ElementFactory";
import { ModdleElement } from "bpmn-js/lib/model/Types";
import BpmnModeler from 'bpmn-js/lib/Modeler';
import Create from 'diagram-js/lib/features/create/Create';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCollapseModule } from "ng-zorro-antd/collapse";
import { NzDropdownModule } from "ng-zorro-antd/dropdown";
import { NzFormModule } from "ng-zorro-antd/form";
import { NzIconModule } from "ng-zorro-antd/icon";
import { NzLayoutModule } from "ng-zorro-antd/layout";
import { NzTabsModule } from "ng-zorro-antd/tabs";
import { NzTooltipModule } from "ng-zorro-antd/tooltip";
import { DndModule } from 'ngx-drag-drop';
import { combineLatest, map, Observable, of } from 'rxjs';
import { ComponentPalleteProvider } from '../../component/component-pallete-provider';
import { BpmEditorToken } from "../editor/bpm-editor";
import BasePaletteProvider from './base-pallete-provider';
import { PaletteGroup, PaletteItem, PaletteProvider } from './palette-provider';

export const PaletteProviders = new InjectionToken<PaletteProvider[]>(
  'PaletteProviders'
);

declare interface PalleteTab {
  name: string;
  paletteGroup: PaletteGroup[];
}

@Component({
  selector: 'bpm-pallete',
  templateUrl: './pallete.html',
  styleUrls: ['pallete.scss'],
  providers: [{
    provide: PaletteProviders,
    useClass: BasePaletteProvider,
    multi: true
  },
  {
    provide: PaletteProviders,
    useClass: ComponentPalleteProvider,
    multi: true
  }
  ]
  ,
  imports: [NzDropdownModule,
    NzIconModule,
    NzLayoutModule,
    MatExpansionModule,
    NzTabsModule,
    NzCollapseModule,
    NzButtonModule,
    NzFormModule,
    DndModule, NzTooltipModule],
  standalone: true,
})
export class BpmPallete implements OnInit {


  bpmEditor = inject(BpmEditorToken);

  bpmnModeler!: BpmnModeler;

  _paletteProviders: PaletteProvider[] = inject(PaletteProviders);

  palleteTabs = signal<PalleteTab[]>([]);

  onDragStart($event: any, tab: number, item: any) {

    this.createElement(item, tab, $event);
  }
  onPaletteItemClick($event: any, tab: number, item: any) {
    this.createElement(item, tab, $event);
  }





  createElement(item: PaletteItem, tab: number, event: any) {

    this.bpmEditor.clearSelection();
    let bpmnFactory: BpmnFactory = this.bpmnModeler.get('bpmnFactory');
    let create: Create = this.bpmnModeler.get('create');
    let elementFactory: ElementFactory = this.bpmnModeler.get('elementFactory');
    let palleteProvider = this._paletteProviders[tab];
    let { type, options } = palleteProvider.getElementOptions(item);
    console.log(options);
    const businessObject: ModdleElement = bpmnFactory.create(type, options);
    var shape = elementFactory.createShape({ type: type, businessObject: businessObject });
    palleteProvider.initElement(this.bpmnModeler, shape, item);
    create.start(event, shape);
  }

  hasMultiPaletteProviders(): boolean {
    return this.palleteTabs().length > 1;
  }

  ngOnInit(): void {

    this.bpmnModeler = this.bpmEditor.bpmnModeler;
    let ps: Observable<{ name: string, paletteGroup: PaletteGroup[] }>[] = this._paletteProviders.map(provider => {
      let paletteGroup = provider.getPaletteGroup();
      if (paletteGroup instanceof Observable) {
        return paletteGroup.pipe(map(
          group => {
            return { name: provider.getName(), paletteGroup: group }
          }
        ));
      }
      else {
        return of({ name: provider.getName(), paletteGroup: paletteGroup });
      }
    });

    combineLatest(ps).subscribe(tabs => {
      this.palleteTabs.set(tabs);
    });

  }
}