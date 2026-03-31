import { Component } from '@angular/core';
import { ConfigOption, FieldType, FieldTypeConfig, FormlyAttributes } from '@ngx-formly/core';
import { IconSelComponent } from '../../biz-components/icon-sel/icon-sel.component';
import { NzInputModule } from 'ng-zorro-antd/input';
import { ReactiveFormsModule } from '@angular/forms';

@Component({
    selector: 'app-field-icon-sel',
    template: `
    
      <nz-input-group [nzAddOnAfter]="selIcon">
          <input  [formControl]="formControl" nz-input />
        </nz-input-group>
        <ng-template #selIcon>
          <app-icon-sel [visible]="selIconVisible" [formlyAttributes]="field" (selIcon)="seledIcon($event)"></app-icon-sel>
        </ng-template>
  `,
    imports: [IconSelComponent, FormlyAttributes, NzInputModule, ReactiveFormsModule],
})
export class IconSelectFieldType extends FieldType<FieldTypeConfig> {

    selIconVisible = false;
    seledIcon(icon: string): void {
        this.formControl.setValue(icon);
    }
}
