import { CommonModule } from "@angular/common";
import { Component, computed, input } from "@angular/core";
import { NzFormModule } from "ng-zorro-antd/form";
import { NzIconModule } from "ng-zorro-antd/icon";
import { NzInputModule } from "ng-zorro-antd/input";
import { NzPopoverModule } from "ng-zorro-antd/popover";
import { NzTooltipModule } from "ng-zorro-antd/tooltip";
import type { PropertyDescription } from "@app/pages/bpm/design/property-panel/types";
import type { BpmnRuntimeVariable } from "./bpmn-runtime-variable.model";


@Component({
    selector: "bpm-readonly-property-row",
    standalone: true,
    imports: [
        CommonModule,
        NzFormModule,
        NzIconModule,
        NzInputModule,
        NzPopoverModule,
        NzTooltipModule,
    ],
    templateUrl: "./readonly-property-row.component.html",
    styleUrls: [
        "./readonly-property-row.component.css",
        "../../../../../shared/formly/formly-field-wrapper.css",
    ],
})
export class ReadonlyPropertyRowComponent {
    propertyDescription = input.required<PropertyDescription>();
    variables = input<BpmnRuntimeVariable[]>([]);
    /**
     * 是否启用「配置值非空但运行时值缺失」的告警。
     * 仅应在流程实例查看场景下的输入/输出 Tab 中开启，避免对设计态或其他只读场景误报。
     */
    runtimeWarningEnabled = input<boolean>(false);

    protected readonly displayName = computed(() => {
        const p = this.propertyDescription();
        const n = p.name?.trim();
        return n && n.length > 0 ? n : p.key;
    });

    /** 与 VerticalFormFieldWrapper 一致：非空说明用标签旁问号 tooltip 展示 */
    protected readonly descriptionTooltip = computed(() => {
        const d = this.propertyDescription().description;
        if (d == null || typeof d !== "string") {
            return undefined;
        }
        const s = d.trim();
        return s.length > 0 ? s : undefined;
    });

    /** 设计时配置：优先 `valueText`，否则回退 `defaultValue`（字符串化） */
    private readonly configuredValue = computed(() => {
        const p = this.propertyDescription();
        if (p.valueText != null && String(p.valueText).length > 0) {
            return String(p.valueText);
        }
        if (p.defaultValue != null && p.defaultValue !== "") {
            return String(p.defaultValue);
        }
        return "";
    });

    // protected readonly mainLine = computed(() => {
    //     const p = this.propertyDescription();
    //     return formatReadonlyPropertyMainLine(this.configuredText(), this.variables(), p.key);
    // });

    protected readonly value = computed(() => {
        if (!this.hasRuntimeValue()) {
            return this.configuredValue();
        }
        const v = this.runtimeRaw();
        if (typeof v === "object" && v !== null) {
            return JSON.stringify(v);
        }
        return String(v);
    });

    protected readonly configuredDetail = computed(() => {
        const t = this.configuredValue();
        return t.length > 0 ? t : "—";
    });

    protected readonly runtimeRaw = computed(() => {
        const key = this.propertyDescription().key;
        return this.variables().find((v) => v?.name === key)?.value;
    });

    protected readonly hasRuntimeValue = computed(() => {
        const v = this.runtimeRaw();
        return v !== undefined && v !== null;
    });

    protected readonly runtimeDetail = computed(() => {
        const v = this.runtimeRaw();
        if (v === undefined || v === null) {
            return undefined;
        }
        if (typeof v === "object") {
            return JSON.stringify(v);
        }
        return String(v);
    });

    /**
     * 由父级显式开启（仅输入/输出 Tab）时，配置值非空但运行时值缺失（null/undefined/空字符串）则告警。
     */
    protected readonly hasRuntimeMissingWarning = computed(() => {
        if (!this.runtimeWarningEnabled()) {
            return false;
        }
        if (this.configuredValue().length === 0) {
            return false;
        }
        const v = this.runtimeRaw();
        if (v === undefined || v === null) {
            return true;
        }
        if (typeof v === "string" && v.length === 0) {
            return true;
        }
        return false;
    });
}
