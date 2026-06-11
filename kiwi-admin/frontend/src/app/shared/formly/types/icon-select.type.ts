import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { FieldType, FieldTypeConfig, FormlyAttributes } from '@ngx-formly/core';

import { NzInputModule } from 'ng-zorro-antd/input';

import { IconSelComponent } from '../../biz-components/icon-sel/icon-sel.component';

@Component({
  selector: 'app-field-icon-sel',
  template: `
    <nz-input-group [nzAddOnAfter]="selIcon">
      <input nz-input [formControl]="formControl" />
    </nz-input-group>
    <ng-template #selIcon>
      <app-icon-sel [formlyAttributes]="field" [visible]="selIconVisible" (selIcon)="seledIcon($event)"></app-icon-sel>
    </ng-template>
  `,
  imports: [IconSelComponent, FormlyAttributes, NzInputModule, ReactiveFormsModule]
})
export class IconSelectFieldType extends FieldType<FieldTypeConfig> {
  selIconVisible = false;
  seledIcon(icon: string): void {
    this.formControl.setValue(icon);
  }
}
