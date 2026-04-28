import { inject, Injectable, InjectionToken } from "@angular/core";
import { Element } from "bpmn-js/lib/model/Types";
import { BasePropertyProvider } from "./base-property-provider";
import { ComponentPropertyProvider } from "../../flow-elements/component-property-provider";
import { PropertyProvider, PropertyTab } from "./types";

export type { PropertyTab, PropertyProvider } from "./types";
export { CAMUNDA_CUSTOM_OUTPUTS_PROPERTY_KEY } from "./types";

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
        const merged = baseTabs.map((t) => ({
            ...t,
            groups: [...t.groups],
        }));
        for (const extra of extraTabs) {
            const idx = merged.findIndex((t) => (t.name ?? "") === (extra.name ?? ""));
            if (idx >= 0) {
                const cur = merged[idx];
                merged[idx] = {
                    ...cur,
                    groups: [...cur.groups, ...extra.groups],
                };
            } else {
                merged.push({
                    ...extra,
                    groups: [...extra.groups],
                });
            }
        }
        return merged;
    }
}

export const PROPERTY_PROVIDER = new InjectionToken<PropertyProvider>('PropertyProvider', {
    providedIn: 'root',
    factory: () => inject(CompositePropertyProvider),
});
