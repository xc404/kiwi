import { inject, Injectable, signal } from "@angular/core";
import { PropertyDescription, PropertyNamespace } from "../design/property-panel/types";
import { BaseHttpService } from "@app/core/services/http/base-http.service";

export declare interface ComponentDescription {
    id: string;
    icon: string;
    key: string
    name: string;
    descrition?: string;
    type: "SpringBean" |"SpringExternalTask" | "CallActivity";
    inputParameters?: PropertyDescription[];
    outputParameters?: PropertyDescription[];
}

export declare interface ComponentsGroup {
    group: string;
    components: ComponentDescription[];
}


@Injectable({ providedIn: 'root' })
export class ComponentProvider {

    // http = inject(BaseHttpService);

    // public componentGroups = signal<ComponentsGroup[]>([]);
    // public components = signal<ComponentDescription[]>([]);
    public componentGroups = signal<ComponentsGroup[]>([]);
    public components = signal<ComponentDescription[]>([]);
    http = inject(BaseHttpService);


    getComponent(id: string): ComponentDescription | undefined {
        return this.components().find(c => c.id == id);
    }

    constructor() {
        this.refresh();
    }

    /** 重新拉取组件列表（另存为组件等场景） */
    refresh(): void {
        this.http.get('/bpm/component/list').subscribe((res: any) => {
            const groups = res.content as ComponentsGroup[];
            groups.forEach(g => {
                g.components.forEach(c => {
                    c.icon = c.icon || 'bpmn-icon86';
                    c.inputParameters = c.inputParameters || [];
                    c.outputParameters = c.outputParameters || [];
                    c.inputParameters.forEach(p => {
                        p.htmlType = p.htmlType || '#text';
                        p.namespace = PropertyNamespace.inputParameter;
                    });
                    c.outputParameters.forEach(p => {
                        p.htmlType = p.htmlType || '#text';
                        p.namespace = PropertyNamespace.inputParameter;
                    });
                })

            });
            this.componentGroups.set(groups);

            const allComponents: ComponentDescription[] = [];
            groups.forEach(g => {
                allComponents.push(...g.components);
            });
            this.components.set(allComponents);

        });
    }

    //     getComponentGroups(){
    //         return this.componentGroups;
    //     }

    //    getComponents(){
    //         return this.components
    //    };

}
