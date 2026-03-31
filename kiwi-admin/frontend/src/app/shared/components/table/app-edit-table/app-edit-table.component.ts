import { NgClass } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, Component, computed, inject, input, model, output } from '@angular/core';


import { NzResizableModule } from 'ng-zorro-antd/resizable';
import { NzTableModule } from 'ng-zorro-antd/table';

import { ColumnConfig, TableCell, TableHeaderCell } from '../column';
import { BaseTableComponent, TableComponentToken } from '../table';
import { FormlyField, FormlyFormBuilder } from "@ngx-formly/core";
import { FieldComp, FieldConfig } from '../../field/field';
import { FieldEditorConfig, toFormlyConfig } from '../../field/field-editor';
import { AppFormlyField } from '@app/shared/formly/app-formly-field';
import { FormGroup } from '@angular/forms';
import { CrudFieldConfig } from '../../crud/utils';





@Component({
  selector: 'edit-cell',
  template: ` <formly-field [field]="_formlyField()"></formly-field> `,
  standalone: true,
  imports: [FormlyField]
})
export class EditCell implements AfterViewInit {

  field = input.required<ColumnConfig>();
  model = model<any>();

  valueChange = output<any>();

  formBuilder = inject(FormlyFormBuilder);
  formGroup = new FormGroup({});

  constructor() {

  }
  ngAfterViewInit(): void {
    this.formGroup.valueChanges.subscribe((value:any) => {
      this.valueChange.emit(value[this.field().dataIndex!]);
    });
  }

  _formlyField = computed(() => {
    let field = toFormlyConfig(this.field(), "column-edit");
    field.modelOptions = {
      updateOn: "blur"
    }
    let formModel: any = {};
    formModel[field.key as string] = this.model();
    this.formBuilder.buildForm(this.formGroup, [field], formModel, {});
    return field;
  });


}

export interface RowChangeEvent {
  row: any;
  column: string;
  value: any
}


@Component({
  selector: 'app-edit-table',
  templateUrl: './app-edit-table.component.html',
  styleUrls: ['./app-edit-table.component.less'],
  providers: [{ provide: TableComponentToken, useExisting: AppEditTableComponent }],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [NzTableModule, NzResizableModule,
    NgClass, TableHeaderCell, TableCell,  EditCell]
})
export class AppEditTableComponent extends BaseTableComponent {


  rowChange = output<RowChangeEvent>();

  toFormlyField(column: ColumnConfig): any {
    return toFormlyConfig({ ...column }, "column-edit");
  }

  editable(field: CrudFieldConfig): boolean {
    return field.edit != false && !!field.dataIndex && field.dataIndex != 'id';
  }

  _rowChange = (row: any, column: ColumnConfig, value: any) => {
    this.rowChange.emit({ row: row, column: column.dataIndex!, value: value });
  }
}


