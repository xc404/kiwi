import { Component, computed, inject, input, output, signal } from "@angular/core";
import { FormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { FormlyFieldConfig, FormlyFormBuilder, FormlyModule } from "@ngx-formly/core";
import { NzButtonModule } from "ng-zorro-antd/button";
import { NzCardModule } from "ng-zorro-antd/card";
import { NzGridModule } from "ng-zorro-antd/grid";
import { NzIconModule } from "ng-zorro-antd/icon";
import { CrudFieldConfig, toFormField } from "../utils";


@Component({
    selector: 'crud-search-form',
    templateUrl: './crud-search-form.html',
    imports: [
        NzCardModule, FormsModule, ReactiveFormsModule, NzGridModule, FormlyModule, NzButtonModule, NzIconModule
    ],
    standalone: true
})
export class CrudSearchForm {
    builder = inject(FormlyFormBuilder);

    /*
    * 搜索默认值
    **/
    searchModel = input({});

    /**
     * 搜索表单字段
     */
    fields = input([] as CrudFieldConfig[]);

    search = output<any>();

    //  searchFormChange = output();

    /**
     * 搜索表单
     */
    searchForm = new FormGroup({});


    formState = signal({});



    /**
     * 搜索表单字段
     */
    searchFormFieldsOptions: any = {};

    collapsed = signal(true);

    defaultSize = 2;

    allFields = computed(() => {
        return this.fields().map(field => {
            let f: FormlyFieldConfig|null =  toFormField(field, 'search') ;
            return f!;
        }).filter((f) => f);
    });

    needCollapse = computed(() => {
        return this.allFields().length > this.defaultSize;
    });

    _searchModel = computed(() => {
        this.formState();
        return {...this.searchModel()};
    });
    
    searchFormFields = computed(() => {
        let fields = this.allFields().filter((f,index) => {
            if(this.collapsed()){
                return index < this.defaultSize;
            }
            return true;
        });
        fields.forEach(f => {
           f.resetOnHide = true;
        });
        this.builder.buildForm(this.searchForm, fields, this._searchModel(), {});
        return fields;
    });

    doSearch(event?: any) {
        this.search.emit(this.searchForm.value);
    }

    onSearchFormChange() {

    }

    reset() {
        this.searchForm.reset();
        this.formState.set({});
        this.doSearch();
    }

    toggleCollapse() {
        this.collapsed.update(v => !v);
    }

}