import { Injectable } from "@angular/core";
import { FormlyFieldConfig } from '@ngx-formly/core';
import { PropertyDescription, toEditFieldConfig } from "./types";


@Injectable(
    {
        providedIn: 'root'
    }
)

export class PropertyFormlyService {

    public toFormlyConfig(p: PropertyDescription): FormlyFieldConfig {

        let config = toEditFieldConfig(p);

        return {};
    }
}