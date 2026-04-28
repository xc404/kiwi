import { Component } from "@angular/core";
import { FieldType, FieldTypeConfig, FormlyFieldProps } from "@ngx-formly/core";

/**
 * 目录声明输出只读展示：数据来自 FormlyFieldProps（label、description 等）与 field.key，
 * 由属性面板将 PropertyDescription 映射进 props。
 */
@Component({
    selector: "declared-output-catalog-type",
    standalone: true,
    template: `
        <div class="declared-output-catalog">
            <code class="select-all key-line">{{ displayKey }}</code>
            @if (showSubtitle) {
                <div class="name-line">{{ props.label }}</div>
            }
            @if (props.description) {
                <div class="desc-line">{{ props.description }}</div>
            }
        </div>
    `,
    styles: [
        `
            .declared-output-catalog {
                display: flex;
                flex-direction: column;
                gap: 6px;
                font-size: 12px;
                line-height: 1.5;
            }
            .key-line {
                user-select: all;
                cursor: text;
                display: block;
                font-size: 12px;
                word-break: break-all;
            }
            .name-line {
                color: rgba(0, 0, 0, 0.75);
            }
            .desc-line {
                color: rgba(0, 0, 0, 0.45);
                word-break: break-word;
            }
        `,
    ],
})
export class DeclaredOutputCatalogType extends FieldType<FieldTypeConfig<FormlyFieldProps>> {
    get displayKey(): string {
        const k = this.field.key;
        if (k == null) {
            return "";
        }
        return Array.isArray(k) ? k.join(".") : String(k);
    }

    get showSubtitle(): boolean {
        const lbl = this.props?.label;
        return !!lbl && String(lbl).trim() !== this.displayKey;
    }
}
