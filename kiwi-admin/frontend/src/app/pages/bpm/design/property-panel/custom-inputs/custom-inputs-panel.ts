import { Component, computed, effect, inject, input, signal, untracked } from "@angular/core";
import { ElementModel } from "@app/pages/bpm/design/extension/element-model";
import { PropertyDescription, PropertyNamespace } from "@app/pages/bpm/design/property-panel/types";
import { NzButtonModule } from "ng-zorro-antd/button";
import { NzDividerModule } from "ng-zorro-antd/divider";
import { NzIconModule } from "ng-zorro-antd/icon";
import BaseViewer from "bpmn-js/lib/BaseViewer";
import { Element } from "bpmn-js/lib/model/Types";
import { ReadonlyPropertyRowComponent } from "@app/pages/bpm/design/property-panel/readonly-property-row/readonly-property-row.component";
import { CustomOutputRowComponent } from "../custom-outputs/custom-output-row.component";
import type { CustomInputRow } from "./custom-input-row.model";
import { ComponentService } from "@app/pages/bpm/flow-elements/component-service";

@Component({
    selector: "bpm-custom-inputs-panel",
    standalone: true,
    imports: [
        NzDividerModule,
        NzButtonModule,
        NzIconModule,
        CustomOutputRowComponent,
        ReadonlyPropertyRowComponent,
    ],
    templateUrl: "./custom-inputs-panel.html",
    styleUrl: "./custom-inputs-panel.css",
})
export class CustomInputsPanel {
    private readonly elementModel = inject(ElementModel);

    bpmnModeler = input.required<BaseViewer>();
    element = input.required<Element>();
    viewMode = input(false);
    variables = input<any[]>([]);

    protected readonly customRows = signal<CustomInputRow[]>([]);
    protected readonly readonlyFlag = computed(() => this.viewMode());

    protected readonly variablesProp = computed(() => {
        const v = this.variables();
        return Array.isArray(v) ? v : [];
    });

    private readonly catalogFingerprint = computed(() =>
        [...this.catalogInputKeys()].sort().join("\0"),
    );
    componentService = inject(ComponentService);

    constructor() {
        effect(() => {
            this.element().id;
            this.bpmnModeler();
            this.catalogFingerprint();
            untracked(() => this.reloadFromModel());
        });
    }

    protected onRowChange(index: number, row: CustomInputRow): void {
        if (this.readonlyFlag()) {
            return;
        }
        const next = this.customRows().map((r, i) => (i === index ? row : r));
        this.customRows.set(next);
        this.flush();
    }

    protected removeRow(index: number): void {
        if (this.readonlyFlag()) {
            return;
        }
        this.customRows.set(this.customRows().filter((_, i) => i !== index));
        this.flush();
    }

    protected addRow(): void {
        if (this.readonlyFlag()) {
            return;
        }
        this.customRows.set([...this.customRows(), { name: "", valueText: "" }]);
    }

    protected toPropertyDescription(row: CustomInputRow): PropertyDescription {
        return {
            key: row.name,
            name: row.name,
            namespace: PropertyNamespace.inputParameter,
            valueText: row.valueText,
        };
    }

    private reloadFromModel(): void {
        const element = this.element();
        const catalog = new Set(this.catalogInputKeys());
        const rows = this.elementModel.getInputParameters(element)
            .map((p: any) => String(p.get("name")))
            .filter((name) => !catalog.has(name))
            .map((name) => ({
                name,
                valueText: this.configuredInputParameterValue(name),
            }));
        this.customRows.set(rows);
    }

    private configuredInputParameterValue(name: string): string {
        const value = this.elementModel.getValue(
            this.bpmnModeler(),
            this.element(),
            PropertyNamespace.inputParameter,
            name,
        );
        return value == null ? "" : String(value);
    }

    catalogInputKeys = computed(() => {
        const component = this.componentService.getComponentForElement(this.element());
        return (component?.inputParameters ?? []).map((p: any) => p.key);
    });

    private flush(): void {
        if (this.readonlyFlag()) {
            return;
        }
        const modeler = this.bpmnModeler();
        const element = this.element();
        const catalog = new Set(this.catalogInputKeys());
        const normalized = this.customRows()
            .map((r) => ({ name: r.name.trim(), valueText: r.valueText }))
            .filter((r) => r.name.length > 0 && !catalog.has(r.name));

        const desired = new Map<string, string>();
        for (const row of normalized) {
            if (!desired.has(row.name)) {
                desired.set(row.name, row.valueText);
            }
        }

        const existingNames = this.elementModel.getInputParameters(element)
            .map((p: any) => String(p.get("name")))
            .filter((name) => !catalog.has(name));
        for (const name of existingNames) {
            if (!desired.has(name)) {
                this.elementModel.removeInputParameter(modeler, element, name);
            }
        }
        for (const [name, valueText] of desired.entries()) {
            this.elementModel.setValue(
                modeler,
                element,
                PropertyNamespace.inputParameter,
                name,
                valueText,
            );
        }
    }
}
