import { Component, computed, input } from '@angular/core';

import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { FieldType } from '@app/shared/components/field/field';

@Component({
  selector: 'app-bpm-project-env',
  standalone: true,
  imports: [CrudPage],
  template: `
    @if (projectId()) {
      <crud-page [pageConfig]="pageConfig()"></crud-page>
    } @else {
      <p class="text-muted">请先选择项目</p>
    }
  `
})
export class BpmProjectEnv {
  projectId = input.required<string | null>();

  pageConfig = computed((): PageConfig => {
    const id = this.projectId();
    return {
      title: '环境变量',
      initializeData: !!id,
      crud: id ? `/bpm/project/${id}/env` : '',
      fields: [
        {
          name: '变量名',
          dataIndex: 'key',
          required: true,
          description: '字母、数字、下划线，如 API_URL、API_KEY'
        },
        {
          name: '值',
          dataIndex: 'value',
          column: false,
          description: '加密项编辑时留空表示不修改'
        },
        {
          name: '加密存储',
          dataIndex: 'encrypted',
          type: FieldType.Boolean,
          description: '敏感信息（密钥、Token）请开启；列表不回显明文'
        },
        {
          name: '说明',
          dataIndex: 'description',
          column: false
        },
        {
          name: '排序',
          dataIndex: 'sort',
          type: FieldType.Int,
          column: false
        }
      ]
    };
  });
}
