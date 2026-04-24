import { Component, computed, effect, inject, input, signal } from "@angular/core";
import { NzSelectModule } from "ng-zorro-antd/select";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { ComponentDescription, ComponentProvider } from "./component-provider";

@Component({
    selector: 'bpm-component-selector',
    template: `
        <nz-select style="width: 100%" [nzOptions]="options()" [formControl]="control()"></nz-select>
    `,
    imports: [NzSelectModule, FormsModule, ReactiveFormsModule],
    standalone: true
})
export class ComponentSelector {

    components = signal<ComponentDescription[]>([]);
    control = input<any>();
    componentService = inject(ComponentProvider);
    constructor() {
        this.loadComponents();
    }

    loadComponents() {
        effect(() => {

            this.components.set(this.componentService.components());
        });

    }

    options = computed(() => {

        return this.components().map(c => ({ ...c, label: c.name, value: c.id }));

    })

}