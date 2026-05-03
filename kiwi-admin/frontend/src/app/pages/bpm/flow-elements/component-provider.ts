import { inject, Injectable, signal } from "@angular/core";
import { BaseHttpService } from "@app/core/services/http/base-http.service";
import { PropertyDescription, PropertyNamespace } from "../design/property-panel/types";

export declare interface ComponentDescription {
    id: string;
    icon: string;
    key: string
    name: string;
    descrition?: string;
    type: "SpringBean" | "SpringExternalTask" | "CallActivity";
    inputParameters?: PropertyDescription[];
    outputParameters?: PropertyDescription[];
    /** 与后端 RecentBpmComponent.lastUsedFromProcessAt 一致（仅 recent-usage 返回） */
    lastUsedFromProcessAt?: string;
}

export declare interface ComponentsGroup {
    group: string;
    components: ComponentDescription[];
}


@Injectable({ providedIn: 'root' })
export class ComponentProvider {


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
                    c.inputParameters = (c.inputParameters || []).map((p) =>
                        this.normalizeInputParameter(p)
                    );
                    c.outputParameters = (c.outputParameters || []).map((p) =>
                        this.normalizeOutputParameter(p)
                    );
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

    private normalizeInputParameter(parameter: PropertyDescription): PropertyDescription {
        const normalized: PropertyDescription = {
            ...parameter,
            namespace: PropertyNamespace.inputParameter,
        };

        if (!normalized.htmlType) {
            normalized.htmlType = "expression";
        }
        if (this.shouldFillRequiredInputDefault(normalized)) {
            normalized.defaultValue = `\${${normalized.key}}`;
        } else if (normalized.defaultValue === undefined || normalized.defaultValue === null) {
            normalized.defaultValue = "";
        }
        return normalized;
    }

    private normalizeOutputParameter(parameter: PropertyDescription): PropertyDescription {
        const normalized: PropertyDescription = {
            ...parameter,
            namespace: PropertyNamespace.declaredOutputParameter,
        };

        if (!normalized.htmlType) {
            normalized.htmlType = "bpm-declared-output";
        }
        if (normalized.defaultValue === undefined || normalized.defaultValue === null) {
            normalized.defaultValue = "";
        }
        return normalized;
    }

    private shouldFillRequiredInputDefault(parameter: PropertyDescription): boolean {
        if (parameter.required !== true || !parameter.key) {
            return false;
        }
        const defaultValue = parameter.defaultValue;
        if (typeof defaultValue === "string") {
            return defaultValue.trim().length === 0;
        }
        return defaultValue === undefined || defaultValue === null;
    }


}
