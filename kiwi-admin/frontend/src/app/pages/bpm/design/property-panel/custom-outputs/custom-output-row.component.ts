import { CommonModule } from "@angular/common";
import { Component, computed, input, output } from "@angular/core";
import type { SpelVariableSuggestion } from "@app/pages/bpm/design/expression/bpm-spel-variable-context";
import { JuelExpressionEditorComponent } from "@app/shared/components/juel-expression-editor/juel-expression-editor.component";
import { FormsModule } from "@angular/forms";
import { NzButtonModule } from "ng-zorro-antd/button";
import { NzIconModule } from "ng-zorro-antd/icon";
import { NzInputModule } from "ng-zorro-antd/input";
import { NzSelectModule } from "ng-zorro-antd/select";
import type { CustomOutputRow } from "./custom-output-row.model";

@Component({
    selector: "bpm-custom-output-row",
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        NzButtonModule,
        NzIconModule,
        NzInputModule,
        NzSelectModule,
        JuelExpressionEditorComponent,
    ],
    templateUrl: "./custom-output-row.component.html",
    styleUrl: "./custom-output-row.component.css",
})
export class CustomOutputRowComponent {
    row = input.required<CustomOutputRow>();
    variables = input<Array<{ name?: string | null; [key: string]: unknown }>>([]);

    rowChange = output<CustomOutputRow>();
    remove = output<void>();

    protected readonly varNames = computed(() => {
        const raw = this.variables();
        if (!Array.isArray(raw)) {
            return [] as string[];
        }
        const names = raw
            .map((v) => (v?.name != null ? String(v.name).trim() : ""))
            .filter((n) => n.length > 0);
        return [...new Set(names)];
    });

    protected readonly expressionVariableSuggestions = computed<SpelVariableSuggestion[]>(() =>
        this.varNames().map((key) => ({ key, source: "referenced" as const })),
    );

    protected readonly refSelection = computed(() => {
        const text = this.row().valueText.trim();
        const m = /^\$\{([a-zA-Z0-9_]+)\}$/.exec(text);
        if (!m) {
            return null;
        }
        return this.varNames().includes(m[1]) ? m[1] : null;
    });

    protected readonly juelInlinePlaceholder = "字面量或 JUEL；$ 补全变量，右侧图标展开编辑";

    protected onNameChange(value: string): void {
        this.rowChange.emit({ ...this.row(), name: value });
    }

    protected onValueChange(value: string): void {
        this.rowChange.emit({ ...this.row(), valueText: value });
    }

    protected onRefSelect(varName: string | null): void {
        if (!varName) {
            return;
        }
        this.rowChange.emit({ ...this.row(), valueText: "${" + varName + "}" });
    }

    protected requestRemove(): void {
        this.remove.emit();
    }
}
