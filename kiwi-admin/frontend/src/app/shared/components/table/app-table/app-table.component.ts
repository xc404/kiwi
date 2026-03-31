import { NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';


import { NzResizableModule } from 'ng-zorro-antd/resizable';
import { NzTableModule } from 'ng-zorro-antd/table';

import { TableCell, TableHeaderCell } from '../column';
import { BaseTableComponent, TableComponentToken } from '../table';



export interface SortFile {
  fileName: string;
  sortDir: undefined | 'desc' | 'asc';
}

@Component({
  selector: 'app-table',
  templateUrl: './app-table.component.html',
  styleUrls: ['./app-table.component.less'],
  providers: [{ provide: TableComponentToken, useExisting: AppTableComponent }],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NzTableModule, NzResizableModule,
     NgClass,  TableHeaderCell, TableCell]
})
export class AppTableComponent extends BaseTableComponent  {
  
}
