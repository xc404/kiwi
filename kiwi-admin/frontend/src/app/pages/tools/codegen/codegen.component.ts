import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, viewChild } from '@angular/core';

import { SessionService } from '@app/core/services/common/session.service';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { ColumnToken } from '@app/shared/components/table/column';
import { Utils } from '@app/utils/utils';
import { TokenKey } from '@config/constant';
import { environment } from '@env/environment';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzConfigService } from 'ng-zorro-antd/core/config';
import { NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzUploadModule } from 'ng-zorro-antd/upload';

import { CodegenPreviewModalComponent } from './codegen-preview-modal';
import { CodegenColumnComponent } from './codegen-fields.component';
import { ImportDatabaseWizardComponent } from './import-database-wizard';

@Component({
  selector: 'app-codegen',
  templateUrl: 'codegen.component.html',
  imports: [PageHeaderComponent, CrudPage, NzCardModule, NzButtonModule, NzIconModule, NzDropDownModule, NzModalModule, NzUploadModule],
  standalone: true
})
export class CodegenComponent {
  importBtn = viewChild('importBtn');
  config = inject(NzConfigService);
  page = viewChild(CrudPage);
  baseApi = '/tools/codegen';
  http = inject(HttpClient);
  modalService = inject(NzModalService);
  messageService = inject(NzMessageService);
  sessionService = inject(SessionService);

  uploadJavaFile(event: any) {
    if (event.file.status === 'done') {
      this.page()?.reloadTable();
    }
  }

  uploadJavaUrl() {
    return Utils.joinUrl(environment.api.baseUrl, this.baseApi, 'entity/import/javaFile');
  }

  pageConfig = computed(() => {
    const importBtn = this.importBtn();
    return {
      title: '表结构',
      initializeData: true,
      crud: '/tools/codegen/entity',
      tableConfig: {
        pageSize: 0
      },
      toolbarActions: [
        {
          template: importBtn
        }
      ],
      columnActions: [
        {
          name: '字段设置',
          handler: () => {
            const record = inject(ColumnToken, { optional: true })?.getRecord();
            this.showColumnsModal(record);
          }
        },
        {
          name: '预览',
          handler: () => {
            const record = inject(ColumnToken, { optional: true })?.getRecord();
            this.preview(record);
          }
        },
        {
          name: '生成代码',
          handler: () => {
            const record = inject(ColumnToken, { optional: true })?.getRecord();
            this.download(record);
          }
        }
      ],
      fields: [
        {
          name: '名称',
          dataIndex: 'tableName',
          required: true
        },
        {
          name: '描述',
          dataIndex: 'tableComment'
        },
        {
          name: '实体类名称',
          dataIndex: 'className'
        },
        {
          name: '持久层类型',
          dataIndex: 'daoTpl',
          column: false,
          editor: 'select',
          options: [
            { label: 'MongoDB', value: 'MongoDB' },
            { label: 'MyBatis-Plus', value: 'MybatisPlus' }
          ]
        },
        {
          name: '前端类型',
          dataIndex: 'webTpl',
          column: false,
          editor: 'select',
          options: [{ label: 'Angular', value: 'Angular' }]
        },
        {
          name: '生成包路径',
          dataIndex: 'packageName',
          column: false
        },
        {
          name: '生成模块名',
          dataIndex: 'moduleName',
          column: false
        },
        {
          name: '生成业务名',
          dataIndex: 'businessName',
          column: false
        },
        {
          name: '生成功能名',
          dataIndex: 'functionName',
          column: false
        },
        {
          name: '生成作者',
          dataIndex: 'functionAuthor',
          column: false
        },
        {
          name: '上级菜单 ID',
          dataIndex: 'parentMenuId',
          column: false
        }
      ]
    } as PageConfig;
  });

  showColumnsModal(record: any) {
    const modalRef = this.modalService.create({
      nzWidth: '75vw',
      nzTitle: '字段设置',
      nzContent: CodegenColumnComponent,
      nzData: record,
      nzFooter: [
        {
          label: '确定',
          onClick: () => {
            modalRef.close();
          }
        }
      ]
    });
  }

  showImportModal() {
    this.modalService
      .create({
        nzWidth: '600px',
        nzTitle: '导入数据库',
        nzContent: ImportDatabaseWizardComponent
      })
      .afterClose.subscribe(() => {
        this.page()?.reloadTable();
      });
  }

  preview(record: any) {
    const modalRef = this.modalService.create({
      nzWidth: '90vw',
      nzTitle: `代码预览 — ${record.className ?? record.tableName}`,
      nzContent: CodegenPreviewModalComponent,
      nzData: { entityId: record.id, className: record.className },
      nzFooter: null
    });
    modalRef.afterClose.subscribe(result => {
      if (result?.download) {
        this.download(record);
      }
    });
  }

  download(record: any) {
    const url = Utils.joinUrl(environment.api.baseUrl, this.baseApi, `entity/${record.id}/download`);
    const token = this.sessionService.getToken();
    this.http
      .get(url, {
        responseType: 'blob',
        headers: token ? { [TokenKey]: token } : {}
      })
      .subscribe({
        next: blob => {
          const filename = `${record.className ?? 'codegen'}-codegen.zip`;
          const objectUrl = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = objectUrl;
          anchor.download = filename;
          anchor.click();
          URL.revokeObjectURL(objectUrl);
          this.messageService.success('下载已开始');
        },
        error: () => {
          this.messageService.error('下载失败');
        }
      });
  }

}
