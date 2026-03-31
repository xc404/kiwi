import { inject, Injectable, InjectionToken } from "@angular/core";
import { Element } from "bpmn-js/lib/model/Types";
import { BasePropertyProvider } from "./base-property-provider";
import { ComponentPropertyProvider } from "./component-property-provider";
import { PropertyProvider, PropertyTab } from "./types";

export type { PropertyTab, PropertyProvider } from "./types";

@Injectable({ providedIn: 'root' })
export class CompositePropertyProvider implements PropertyProvider {

    private base = inject(BasePropertyProvider);
    private component = inject(ComponentPropertyProvider);

    getProperties(element: Element): PropertyTab[] {
        const baseTabs = this.base.getProperties(element);
        const extraTabs = this.component.getProperties(element);
        if (extraTabs.length === 0) {
            return baseTabs;
        }
        const merged = [...baseTabs];
        const first = merged[0];
        merged[0] = {
            ...first,
            groups: [...first.groups, ...extraTabs[0].groups]
        };
        if (extraTabs.length > 1) {
            merged.push(...extraTabs.slice(1));
        }
        return merged;
    }
}

export const PROPERTY_PROVIDER = new InjectionToken<PropertyProvider>('PropertyProvider', {
    providedIn: 'root',
    factory: () => inject(CompositePropertyProvider),
});
