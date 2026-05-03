import { Component } from "@angular/core";
import { FieldType, FieldTypeConfig, FormlyFieldProps } from "@ngx-formly/core";
import { NzIconModule } from "ng-zorro-antd/icon";
import { NzTooltipModule } from "ng-zorro-antd/tooltip";

@Component({
    selector: "bpm-declared-output-type",
    standalone: true,
    imports: [NzIconModule, NzTooltipModule],
    template: `
        <div class="declared-output-line">
            <span class="name-text">{{ displayName }}</span>
                <span
                    class="help-icon"
                    nz-icon
                    nzType="question-circle"
                    nzTheme="outline"
                    nz-tooltip
                    [nzTooltipTitle]="displayDescription"
                ></span>
        </div>
    `,
    styles: [
        `
            .declared-output-line {
                display: inline-flex;
                align-items: center;
                gap: 6px;
                min-height: 24px;
                line-height: 1.4;
            }
            .name-text {
                color: rgba(0, 0, 0, 0.88);
                font-size: 13px;
                font-weight: 500;
                word-break: break-word;
            }
            .help-icon {
                color: rgba(0, 0, 0, 0.45);
                font-size: 14px;
                cursor: help;
            }
        `,
    ],
})
export class BpmDeclaredOutputType extends FieldType<FieldTypeConfig<FormlyFieldProps>> {
    get displayName(): string {
        const label = this.props?.label;
        if (label != null && String(label).trim().length > 0) {
            return String(label);
        }
        const key = this.field.key;
        if (key == null) {
            return "";
        }
        return Array.isArray(key) ? key.join(".") : String(key);
    }

    get displayDescription(): string {
        const d = this.props?.description;
        return d == null ? "" : String(d).trim();
    }
}
