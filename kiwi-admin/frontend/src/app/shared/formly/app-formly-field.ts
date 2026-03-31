import { Component, computed, inject, input, model } from "@angular/core";
import { FormGroup } from "@angular/forms";
import { FormlyField, FormlyFieldConfig, FormlyFormBuilder } from "@ngx-formly/core";


@Component({
    selector: 'app-formly-field',
    template: ` <formly-field [field]="_formlyField()"></formly-field> `,
    standalone: true,
    imports: [FormlyField]
})
export class AppFormlyField {

    field = input.required<FormlyFieldConfig>();
    model = model<any>();

    formBuilder = inject(FormlyFormBuilder);
    formGroup = new FormGroup({});

    _formlyField = computed(() => {
        let field = this.field();
        let formModel: any = {};
        formModel[field.key as string] = this.model();
        this.formBuilder.buildForm(this.formGroup, [field], formModel, {});
        return field;
    });
}