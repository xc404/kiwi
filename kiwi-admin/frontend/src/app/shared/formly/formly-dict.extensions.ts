import { FormlyExtension, FormlyFieldConfig } from "@ngx-formly/core";
import { IDictService } from "../dict/dict";


export class FormlyDictExtension implements FormlyExtension {
  constructor(private dictService: IDictService) {
    
  }
  prePopulate(field: FormlyFieldConfig) {
    const props = field.props || {};
    if(!props['dictKey']){
        return;
    }
    if (props.options) {
      return;
    }
    props.options = [...this.dictService.getDictGroup(props['dictKey'])];
    props['valueProp'] = 'code';
    props['labelProp'] = 'name';
  }
}
