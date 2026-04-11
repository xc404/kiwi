import { Component, computed, effect, inject, input, OnInit, output, signal } from "@angular/core";
import { FormGroup } from "@angular/forms";
import { BaseHttpService } from "@app/core/services/http/base-http.service";
import { AppButton } from "@app/shared/components/button/app.button";
import { CrudEditForm } from "@app/shared/components/crud/components/crud-edit-form";
import { FieldType } from "@app/shared/components/field/field";
import { Editor } from "@app/shared/components/field/field-editor";
import { AppTableComponent } from "@app/shared/components/table/app-table/app-table.component";
import { actionColumn, ColumnToken } from "@app/shared/components/table/column";
import { AppTableConfig } from "@app/shared/components/table/table";
import { NzCollapseModule } from "ng-zorro-antd/collapse";
import { NzModalModule } from "ng-zorro-antd/modal";
import { PropertyDescription } from "../design/property-panel/types";
import { ComponentDescription } from "./component-provider";
@Component({

    selector: 'app-bpm-parameters',
    template: ` 
     <nz-collapse>
              <nz-collapse-panel [nzHeader]="name()" [nzActive]="true" [nzExtra]="toolbar">
            <app-table [tableConfig]="tableConfig" [tableData]="_parameters()" >


             </app-table>

            </nz-collapse-panel>
            </nz-collapse>
            <ng-template let-record>
                <span>{{record.htmlType}}</span>
            </ng-template>
  <nz-modal nzxModalDrag [nzVisible]="editModalVisible()" [nzTitle]="'参数设置'" (nzOnOk)="saveItem()"
    (nzOnCancel)="editModalVisible.set(false)" [nzWidth]="'60vw'">
    <div *nzModalContent class="crud-edit-form">
      <crud-edit-form [form]="editForm" [record]="editRecord()" [fields]="editFormFields()"
        [columns]="2"></crud-edit-form>
    </div>
  </nz-modal>

  <ng-template #toolbar>
     <div class="ant-pro-table-toolbar-option">
        @for (item of toolbarActions; track $index) {
          <app-button  [config]="item" class="m-l-10"></app-button>
        }
      </div>
    </ng-template>
     `,
    standalone: true,
    imports: [NzCollapseModule, AppTableComponent, NzModalModule, CrudEditForm, AppButton]
})
export class BpmParameters implements OnInit {
    http: any = inject(BaseHttpService);
    parameters = input.required<PropertyDescription[]>();

    editModalVisible = signal(false);
    name = input.required<string>();

    editForm = new FormGroup({});

    _parameters = signal<any[]>([]);

    editRecord = signal<any>(null);

    parameterChange = output<PropertyDescription[]>();
    editModel = "edit";

    toolbarActions = [
        {
            name: '添加参数', handler: () => {
                this.editParameter({}, 'add');
            }
        }
    ]
    tableConfig: AppTableConfig = {

        columns: [
            { name: '键', dataIndex: 'key' },

            { name: '名称', dataIndex: 'name' },

            { name: '描述', dataIndex: 'description' },
            { name: '默认值', dataIndex: 'defaultValue' },
            { name: '是否必填', dataIndex: 'required', type: FieldType.Boolean },
            { name: '只读', dataIndex: 'readonly', type: FieldType.Boolean },
            { name: '隐藏', dataIndex: 'hidden', type: FieldType.Boolean },
            { name: '编辑器', dataIndex: 'htmlType', dictKey: 'field_editor' },
            { name: '示例', dataIndex: 'example', editor: Editor.TextArea },
            { name: '字典键', dataIndex: 'dictKey' },
            actionColumn({
                name: '操作',
                fixed: true,// 是否固定单元格 （只有从最左边或最右边连续固定才有效）
                fixedDir: 'right',
                width: 300,
                actions: [
                    {
                        name: '上移', handler: () => {
                            let record = inject(ColumnToken).getRecord();
                            this.moveParameter(record, 'up');
                        }
                    },
                    {
                        name: '下移', handler: () => {
                            let record = inject(ColumnToken).getRecord();

                            this.moveParameter(record, 'down');
                        }
                    },
                    {
                        name: '编辑', handler: () => {
                            let record = inject(ColumnToken).getRecord();
                            this.editParameter(record, 'edit');
                        }
                    },
                    {
                        name: '删除', handler: () => {
                            let record = inject(ColumnToken).getRecord();
                            this.deleteParameter(record);
                        }
                    },
                ]
            }),
        ]
    }

    editFormFields = computed(() => {
        return this.tableConfig.columns.filter(c => c.dataIndex);
    });


    constructor() {
        effect(() => {
            this._parameters.set(this.parameters());
        });
    }
    ngOnInit(): void {
        // this.editForm.get("parentId")?.valueChanges.subscribe(value => {
        //     console.log('Parent ID changed:', value);
        // });
        // this.editForm.valueChanges.subscribe(value => {
        //     console.log('Form changes:', value);
        // });
    }


    moveParameter(record: any, direction: 'up' | 'down') {
        const index = this._parameters().indexOf(record);
        if (index < 0) return;
        const newIndex = direction === 'up' ? index - 1 : index + 1;
        if (newIndex < 0 || newIndex >= this._parameters().length) return;
        const parameters = [...this._parameters()];
        [parameters[index], parameters[newIndex]] = [parameters[newIndex], parameters[index]];
        this.setParameters(parameters);
    }

    getParameters() {
        return this._parameters();
    }

    editParameter(record: any, model: 'edit' | 'add') {
        this.editModel = model;
        this.editRecord.set({ ...record });
        this.editModalVisible.set(true);
        if (model === 'edit') {

        }
    }

    deleteParameter(record: any) {
        const parameters = this._parameters().filter((item) => item.key !== record.key);
        this.setParameters(parameters);
    }




    saveItem() {


        const record = this.editRecord();
        if (this.editModel === 'add') {
            this.setParameters([...this._parameters(), record]);
        } else {
            const index = this._parameters().findIndex((item) => item.key === record.key);
            if (index >= 0) {
                const parameters = [...this._parameters()];
                parameters[index] = record;
                this.setParameters(parameters);
            }
        }
        this.editModalVisible.set(false);
    }

    setParameters(parameters: PropertyDescription[]) {

        this._parameters.set(parameters);
        this.parameterChange.emit(parameters);
        this.editModalVisible.set(false);
    }

}

@Component({

    selector: 'app-bpm-input-output-parameters',
    template: ` 
        <app-bpm-parameters [name]="'输入'" [parameters]="inputParams()" (parameterChange)="saveParamters($event, 'input')"></app-bpm-parameters>

        <app-bpm-parameters [name]="'输出'"  [parameters]="outputParams()" (parameterChange)="saveParamters($event, 'output')"></app-bpm-parameters>
     `,
    standalone: true,
    imports: [NzCollapseModule, BpmParameters]
})
export class BpmInputOutputParameters {

    component = input.required<ComponentDescription>();
    http: any = inject(BaseHttpService);


    inputParams = computed(() => {
        return this.component().inputParameters || [];
    });

    outputParams = computed(() => {
        return this.component().outputParameters || [];
    });

    saveParamters(parameters: PropertyDescription[], type: 'input' | 'output' = 'input') {
        let body: any = {
            id: this.component().id,

        }
        if (type === 'input') {
            body.inputParameters = parameters;
        } else {
            body.outputParameters = parameters;
        }
        this.http.put(`/bpm/component/${this.component().id}`, body).subscribe(() => {



            // 保存成功后的逻辑
        });

    }

}