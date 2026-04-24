import { Component, DestroyRef, inject, OnInit, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { ColumnToken } from '@app/shared/components/table/column';
import { Editor } from '@app/shared/components/field/field-editor';

@Component({
  selector: 'app-bpm-process-instances',
  template: `
    <app-page-header></app-page-header>
    <section class="page-content">
      <crud-page [pageConfig]="pageConfig"></crud-page>
    </section>
  `,
  imports: [PageHeaderComponent, CrudPage],
})
export class BpmProcessInstances implements OnInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);
  private crudPage = viewChild(CrudPage);

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(qm => {
      const key = qm.get('processDefinitionKey');
      if (!key) {
        return;
      }
      this.pageConfig = {
        ...this.pageConfig,
        search: {
          ...this.pageConfig.search,
          basicParams: {
            ...this.pageConfig.search?.basicParams,
            instanceState: 'running',
            processDefinitionKey: key,
          },
        },
      };
      const page = this.crudPage();
      if (page) {
        page.load({
          instanceState: 'running',
          processDefinitionKey: key,
        });
      }
    });
  }

  pageConfig: PageConfig = {
    title: '运行实例',
    search: {
      basicParams: {
        instanceState: 'running',
      },
    },
    initializeData: true,
    editModal: { disabled: true },
    tableConfig: {
      showCheckbox: false,
      pageSize: 20,
    },
    crud: {
      search: '/bpm/process-instance',
      create: false,
      update: false,
      delete: false,
      get: false,
    },
    columnActions: [
      {
        icon: 'eye',
        tooltip: '查看',
        handler: () => {
          const record = inject(ColumnToken, { optional: true })?.getRecord();
          if (record?.id) {
            const url = new URL(window.location.href);
            url.hash = this.router.serializeUrl(
              this.router.createUrlTree(['/bpm/process-instance', record.id])
            );
            window.open(url.toString(), '_blank', 'noopener,noreferrer');
          }
        },
      },
    ],
    fields: [
      {
        name: '流程定义 Key',
        dataIndex: 'processDefinitionKey',
        width: 280,
        search: true,
        edit: false,
      },
      {
        name: '实例状态',
        dataIndex: 'instanceState',
        width: 140,
        search: true,
        edit: false,
        defaultValue: 'running',
        editor: Editor.ComboBox,
        options: [
          { label: '运行中', value: 'running' },
          { label: '已结束', value: 'completed' },
          { label: '全部', value: 'all' },
        ],
      },
      {
        name: '实例 ID',
        dataIndex: 'id',
        width: 280,
        search: true,
        edit: false,
      },
      {
        name: '业务键',
        dataIndex: 'businessKey',
        search: true,
        edit: false,
      },
     
      {
        name: '流程名称',
        dataIndex: 'processDefinitionName',
        edit: false,
      },
      {
        name: '开始时间',
        dataIndex: 'startTime',
        width: 180,
        edit: false,
      },
      {
        name: '租户',
        dataIndex: 'tenantId',
        width: 120,
        edit: false,
      },
    ],
  };
}
