import { Component, computed, effect, inject, input, signal, untracked } from "@angular/core";
import { ElementModel } from "@app/pages/bpm/design/extension/element-model";
import { PropertyNamespace } from "@app/pages/bpm/design/property-panel/types";
import { NzButtonModule } from "ng-zorro-antd/button";
import { NzDividerModule } from "ng-zorro-antd/divider";
import { NzIconModule } from "ng-zorro-antd/icon";
import BaseViewer from "bpmn-js/lib/BaseViewer";
import { Element } from "bpmn-js/lib/model/Types";
import { CustomOutputRowComponent } from "./custom-output-row.component";
import type { CustomOutputRow } from "./custom-output-row.model";

@Component({
    selector: "bpm-custom-outputs-panel",
    standalone: true,
    imports: [NzDividerModule, NzButtonModule, NzIconModule, CustomOutputRowComponent],
    templateUrl: "./custom-outputs-panel.html",
    styleUrl: "./custom-outputs-panel.css",
})
export class CustomOutputsPanel {
    private readonly elementModel = inject(ElementModel);

    bpmnModeler = input.required<BaseViewer>();
    element = input.required<Element>();
    viewMode = input(false);
    variables = input<any[]>([]);
    catalogOutputKeys = input<string[]>([]);

    protected readonly customRows = signal<CustomOutputRow[]>([]);
    protected readonly readonlyFlag = computed(() => this.viewMode());

    protected readonly variablesProp = computed(() => {
        const v = this.variables();
        return Array.isArray(v) ? v : [];
    });

    private readonly catalogFingerprint = computed(() =>
        [...this.catalogOutputKeys()].sort().join("\0"),
    );

    constructor() {
        effect(() => {
            this.element().id;
            this.bpmnModeler();
            this.catalogFingerprint();
            untracked(() => this.reloadFromModel());
        });
    }

    protected onRowChange(index: number, row: CustomOutputRow): void {
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

    private reloadFromModel(): void {
        const element = this.element();
        const catalog = new Set(this.catalogOutputKeys());
        const rows = this.elementModel.getOutputParameters(element)
            .map((p: any) => String(p.get("name")))
            .filter((name) => !catalog.has(name))
            .map((name) => ({
                name,
                valueText: this.readOutputValue(name),
            }));
        this.customRows.set(rows);
    }

    private readOutputValue(name: string): string {
        const value = this.elementModel.getValue(
            this.bpmnModeler(),
            this.element(),
            PropertyNamespace.outputParameter,
            name,
        );
        if (!this.viewMode()) {
            return value == null ? "" : String(value);
        }
        const rawValue = this.variablesProp().find(v => v.name === name)?.value;
        if (!value && !rawValue) {
            return "";
        }
        if (rawValue !== undefined && rawValue !== value) {
            return `${rawValue} (${value})`;
        }
        return value == null ? "" : String(value);
    }

    private flush(): void {
        if (this.readonlyFlag()) {
            return;
        }
        const modeler = this.bpmnModeler();
        const element = this.element();
        const catalog = new Set(this.catalogOutputKeys());
        const normalized = this.customRows()
            .map((r) => ({ name: r.name.trim(), valueText: r.valueText }))
            .filter((r) => r.name.length > 0 && !catalog.has(r.name));

        const desired = new Map<string, string>();
        for (const row of normalized) {
            if (!desired.has(row.name)) {
                desired.set(row.name, row.valueText);
            }
        }

        const existingNames = this.elementModel.getOutputParameters(element)
            .map((p: any) => String(p.get("name")))
            .filter((name) => !catalog.has(name));
        for (const name of existingNames) {
            if (!desired.has(name)) {
                this.elementModel.removeOutputParameter(modeler, element, name);
            }
        }
        for (const [name, valueText] of desired.entries()) {
            this.elementModel.setValue(
                modeler,
                element,
                PropertyNamespace.outputParameter,
                name,
                valueText,
            );
        }
    }
}
