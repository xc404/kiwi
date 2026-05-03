import { DatePipe } from "@angular/common";
import { Component, computed, inject, input, model } from "@angular/core";
import { IDictService } from "../../dict/dict";

export enum FieldType {
    Int = "Int",
    Double = "Double",
    Date = "Date",
    DateTime = "DateTime",
    String = "String",
    Boolean = "Boolean",
    Enum = "Enum",
}



export interface FieldConfig {
    name: string;
    description?: string;
    dataIndex?: string;
    type?: FieldType;
    dictKey?: string;
    format?: string | ((value: any) => string);
   
}


@Component({
    template: ""
})
export abstract class FieldComp {

    readonly field = input.required<FieldConfig | any>();
    readonly record = input<any>();
    readonly value = model<any>();
    readonly dictService: IDictService = inject<IDictService>(IDictService);
    readonly datepipe = new DatePipe('en-US');

    displayValue = computed(() => {
        let field = this.field();
        let value = this.getValue();
        if (!field) {
            return value;
        }
        if (field.format) {
            if (typeof field.format === "function") {
                return field.format(value);
            }
            if (typeof field.format === "string") {
                return field.format.replace("{0}", value);
            }
        }
        if (field.dictKey) {
            return this.dictService.getDictValue(field.dictKey || "", value);
        }
        if (field.type == FieldType.Date) {
            return this.datepipe.transform(value, field().format || "yyyy-MM-dd");
        }
        if (field.type == FieldType.DateTime) {
            return this.datepipe.transform(value, field().format  || "yyyy-MM-dd HH:mm:ss");
        }
        return value;
    })


    dicts = computed(() => {
        if (this.value() && this.field().dictKey) {
            return this.dictService.getDictGroup(this.field().dictKey as string);
        }
        return [];
    })
    name = computed(() => {
        if (this.field()) {
            return this.field()?.name;
        }
        return undefined as unknown as string
    })

    dataIndex = computed(() => {
        return this.field().dataIndex || this.field().name;
    })

    getValue() {
        if (this.record() && this.field()) {
            return this.record()[this.dataIndex()];
        }
        return this.value();
    }


    protected _getValue = computed(() => {
        if (this.record() && this.field()) {
            return this.record()[this.dataIndex()];
        }
        return this.value();
    })



}


