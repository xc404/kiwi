import { inject, Injectable } from "@angular/core";
import { toObservable } from "@angular/core/rxjs-interop";
import { Element } from "bpmn-js/lib/model/Types";
import BpmnModeler from 'bpmn-js/lib/Modeler';
import { map, Observable } from "rxjs";
import { PaletteGroup, PaletteItem, PaletteProvider } from "../design/palette/palette-provider";
import { ComponentService } from "./component-service";
import { ComponentProvider } from "./component-provider";


@Injectable()
export class ComponentPalleteProvider implements PaletteProvider {


    palettes: Observable<PaletteGroup[]>;

    componentService: ComponentService = inject(ComponentService);

    componentProvider = inject(ComponentProvider);

    constructor() {
        this.palettes = toObservable(this.componentProvider.componentGroups).pipe(
            map(groups => {
                return groups.map(g => {
                    const paletteGroup: PaletteGroup = {
                        group: g.group,
                        palettes: g.components.map(c => {
                            const paletteItem: PaletteItem = this.componentService.convertComponentToPalette(c);
                            return paletteItem;
                        })
                    }
                    return paletteGroup;
                });

            })
        );
    }



    getName(): string {
        return "业务组件"
    }
    getPaletteGroup(): Observable<PaletteGroup[]> {

        return this.palettes;
    }

    // getPallteType(component:ComponentDescription): string    {
    //     return component.type;
    getElementOptions(item: PaletteItem): { type: any; options: any; } {
        return this.componentService.getElementOptions(item);
    }


    initElement(bpmnModeler: BpmnModeler, element: Element, item: PaletteItem): void {
        this.componentService.initElement(bpmnModeler, element, item);
    }


}