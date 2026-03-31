import { Component, computed, input } from "@angular/core";
import { FormGroup, ReactiveFormsModule } from "@angular/forms";
import { FormlyFieldConfig, FormlyModule } from "@ngx-formly/core";
import { CrudFieldConfig, toFormField } from "../utils";
import { FormPanel } from "@app/shared/formly/panel/form-panel";


export type EditMode = 'create' | 'update';

@Component({
    selector: 'crud-edit-form',
    templateUrl: './crud-edit-form.html',
    imports: [
    ReactiveFormsModule,
     FormlyModule,
    FormPanel
],
    standalone: true
})
export class CrudEditForm {
    form = input(new FormGroup({}));

    record = input({} as any);

    fields = input([] as CrudFieldConfig[]);

    columns = input(2);

    editMode = input<EditMode>('create');

    editFormFields = computed(() => {
       return this.fields().map(field => {
            let f: FormlyFieldConfig|null =  toFormField(field, this.editMode()) ;
            return f!;
        }).filter((f) => f);
    });

    getFormValues(){
        return this.form().value;
    }    
}