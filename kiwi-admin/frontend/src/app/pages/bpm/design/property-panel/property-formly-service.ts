import { Injectable, inject } from "@angular/core";
import { FormlyFieldConfig } from '@ngx-formly/core';
import { ElementModel } from "../extension/element-model";
import { PropertyDescription, toEditFieldConfig } from "./types";


@Injectable(
    {
        providedIn: 'root'
    }
)

export class PropertyFormlyService {

    private readonly elementModel = inject(ElementModel);

    public toFormlyConfig(p: PropertyDescription): FormlyFieldConfig {

        let config = toEditFieldConfig(p, this.elementModel);

        return {};
    }
}