import { Component, computed, input } from "@angular/core";
import { FormGroup, ReactiveFormsModule } from "@angular/forms";
import { FormlyFieldConfig, FormlyModule } from "@ngx-formly/core";

@Component({
    selector: 'app-formly-panel',
    templateUrl: './form-panel.html',
    styleUrls: ['./form-panel.css'],
    standalone: true,
    imports: [
        FormlyModule,
        ReactiveFormsModule
    ]
})
export class FormPanel {


    form = input<FormGroup>(new FormGroup({}));

    model = input({} as any);

    fields = input<FormlyFieldConfig[]>([]);

    wrapper = input<string>('edit-form');


    columns = input(1);


    layoutFields = computed(() => {

        this.fields().forEach(field => {
            field.wrappers = [this.wrapper()];
        });

        return [
            {

                fieldGroupClassName: 'ant-row app-formly-panel-row',
                fieldGroup: this.fields().map(field => {
                    let className = (field.className || '') + ' ant-col-' + (24 / this.columns());
                    if (this.columns() > 1) {
                        className += ' padding-right-24';
                    }
                    console.log(field.className, className);
                    return {
                        ...field,
                        className: className
                    }
                })
            }
        ]
    });
}