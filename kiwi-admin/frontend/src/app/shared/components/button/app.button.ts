import { CommonModule } from "@angular/common";
import { Component, computed, inject, Injector, input, runInInjectionContext, TemplateRef } from "@angular/core";
import { NzButtonModule, NzButtonShape, NzButtonSize, NzButtonType } from "ng-zorro-antd/button";
import { NzIconModule } from "ng-zorro-antd/icon";
import { NzTooltipDirective } from "ng-zorro-antd/tooltip";


export interface AppButtonConfig {
    name?: string;
    tooltip?: string;
    icon?: string;
    nzGhost?: boolean;
    nzSearch?: boolean;
    nzLoading?: boolean;
    nzDanger?: boolean;
    disabled?: boolean | (() => boolean);
    visible?: boolean | (() => boolean);
    nzType?: NzButtonType;
    nzShape?: NzButtonShape;
    nzSize?: NzButtonSize;
    handler?: () => any;
    template?: TemplateRef<any>;
}

@Component({
    selector: 'app-button',
    styles: '',
    template: `
        <!-- @if (visible()) {  -->
           
        @if (config().template) {  
            <div>
            </div>
         <ng-container  [ngTemplateOutlet]="config().template"  [ngTemplateOutletContext]="{config:config()}"></ng-container>
        }@else {
 <button nz-button   [title]="config().name" 
         [nzType]="config().nzType||null"
          [nzGhost]="config().nzGhost"
          [nzSearch]="config().nzSearch"
          [nzLoading]="config().nzLoading"
          [nzDanger]="config().nzDanger"
          [disabled]="disabled()"
          [nzShape]="config().nzShape||null"
          [nzSize]="config().nzSize||'default'"  
          [nz-tooltip]="config().tooltip" (click)="handle(); $event.stopPropagation()">
            @if (config().icon) {
                <nz-icon [nzType]="config().icon!" nzTheme="outline"></nz-icon>
            }
            {{config().name}}
        </button>
        }
     
        <!-- } -->
       
    `,
    imports: [CommonModule, NzIconModule, NzButtonModule, NzTooltipDirective],
    standalone: true
})
export class AppButton {
    config = input.required<AppButtonConfig>();
    injector = inject(Injector);
    handle() {
        runInInjectionContext(this.injector, () => {
            this.config().handler?.call(this);
        });
    }

    disabled = computed(() => {
        if (typeof this.config().disabled === 'function') {
            let handler: any = this.config().disabled;
            return runInInjectionContext(this.injector, () => {
                return handler.call(this)
            });
        }
        return this.config()?.disabled;
    });

    visible = computed(() => {
        if (typeof this.config().visible === 'function') {
            let handler: any = this.config().visible;
            return runInInjectionContext(this.injector, () => {
                return handler.call(this)
            });
        }
        return this.config()?.visible != false;
    });
}

