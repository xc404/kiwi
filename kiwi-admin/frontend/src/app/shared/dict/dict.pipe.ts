/*
使用方法：
accidentTypeOptions: OptionsInterface[];
this.accidentTypeOptions = [...MapPipe.transformMapToArray(MapSet.accidentType)];
*/

import { DatePipe } from '@angular/common';
import { inject, Pipe, PipeTransform } from '@angular/core';

import { NzSafeAny } from 'ng-zorro-antd/core/types';
import { IDictService } from './dict';

export const enum DateFormat {
  Date = 'yyyy-MM-dd',
  DateHour = 'yyyy-MM-dd HH',
  DateTime = 'yyyy-MM-dd HH:mm'
}



export interface MapItem {
  label: string;
  value: NzSafeAny;
}

@Pipe({
  name: 'map',
  standalone: true
})
export class DictPipe implements PipeTransform {
  private datePipe: DatePipe = new DatePipe('en-US');
   readonly dictService: IDictService = inject<IDictService>(IDictService);
//   private mapObj = MapSet;


  transform(value: NzSafeAny, arg?: NzSafeAny): NzSafeAny {
    if (arg === undefined) {
      return value;
    }
    let type: string = arg;
    let param = '';

    if (arg.indexOf(':') !== -1) {
      type = arg.substring(0, arg.indexOf(':'));
      param = arg.substring(arg.indexOf(':') + 1, arg.length);
    }

    switch (type) {
      case 'date':
        return this.datePipe.transform(value, param);
      default:
        // @ts-ignore
        return this.dictService.getDictValue(type, value);
    }
  }
}
