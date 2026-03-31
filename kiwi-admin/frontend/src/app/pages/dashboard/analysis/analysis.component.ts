import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { MenuStoreService } from '@app/core/services/store/common-store/menu-store.service';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';

import { AppTableWrapComponent } from '@app/shared/components/table/app-table-wrap/app-table-wrap.component';
import { AppTableComponent } from '@app/shared/components/table/app-table/app-table.component';
import { AppTableConfig } from '@app/shared/components/table/table';
import { ScreenLessHiddenDirective } from '@shared/directives/screen-less-hidden.directive';
import { NumberLoopPipe } from '@shared/pipes/number-loop.pipe';

import { NzBadgeModule } from 'ng-zorro-antd/badge';
import { NzBreadCrumbModule } from 'ng-zorro-antd/breadcrumb';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzDatePickerModule } from 'ng-zorro-antd/date-picker';
import { NzDividerModule } from 'ng-zorro-antd/divider';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTabsModule } from 'ng-zorro-antd/tabs';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';
import { NzTypographyModule } from 'ng-zorro-antd/typography';

interface DataItem {
  name: string;
  chinese: number;
  math: number;
  english: number;
}

@Component({
  selector: 'app-analysis',
  templateUrl: './analysis.component.html',
  styleUrls: ['./analysis.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NzCardModule,
    NzBreadCrumbModule,
    NzGridModule,
    NzIconModule,
    NzButtonModule,
    NzTooltipModule,
    NzDividerModule,
    NzTabsModule,
    NzBadgeModule,
    NzRadioModule,
    NzDatePickerModule,
    NzTypographyModule,
    NzTableModule,
    PageHeaderComponent,
    CrudPage
  ]
})
export class AnalysisComponent {
  menuStore = inject(MenuStoreService);
  destroyRef = inject(DestroyRef);

  dataList = [
    {
      id: '1',
      noShow: '默认不展示',
      longText: '文字超级长文字超级长文字超级长文字超级长文字超级长文字超级长',
      newline: '没有省略号没有省略号没有省略号没有省略号没有省略号没有省略号没有省略号没有省略号',
      addStyle: '加样式',
      name: '自定义模板',
      obj: { a: { b: '点出来的值1' } }
    },
    {
      id: '2',
      noShow: '默认不展示',
      longText: '文字超级长',
      newline: 'string',
      name: '自定义模板',
      addStyle: '加样式',
      obj: { a: { b: '点出来的值1' } }
    },
    {
      id: '3',
      noShow: '默认不展示',
      longText: 'string',
      newline: 'string',
      name: '自定义模板',
      addStyle: '加样式',
      obj: { a: { b: '点出来的值1' } }
    },
    {
      id: '4',
      noShow: '默认不展示',
      longText: 'string',
      newline: 'string',
      name: '自定义模板',
      addStyle: '加样式',
      obj: { a: { b: '点出来的值1' } }
    },
    {
      id: '5',
      noShow: '默认不展示',
      longText: 'string',
      newline: 'string',
      name: '自定义模板',
      addStyle: '加样式',
      obj: { a: { b: '点出来的值1' } }
    },
    {
      id: '6',
      noShow: '默认不展示',
      longText: 'string',
      newline: 'string',
      name: '自定义模板',
      addStyle: '加样式',
      obj: { a: { b: '点出来的值1' } }
    }
  ];


  pageConfig: PageConfig = {
    title: "测试",
    crud: "system/menu",
    tableConfig: {
      showCheckbox: true,
      type: 'tree',
      enableTreeSelection: true,
    },
    fields: [
      {
        name: '名称',
        dataIndex: 'menuName',
        search: true
      },
      {
        name: '文字很长',
        dataIndex: 'longText',
        search: true,
        showSort: true
      },
      {
        name: '换行',
        dataIndex: 'newline',
        notNeedEllipsis: true,
        search: true,
        showSort: true,
        tdClassList: ['text-wrap']
      },
      {
        name: '加样式',
        dataIndex: 'addStyle',
        search: true,
        tdClassList: ['operate-text']
      }
    ]
  };
}




