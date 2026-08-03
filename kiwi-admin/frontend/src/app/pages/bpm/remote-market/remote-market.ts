import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzRadioModule } from 'ng-zorro-antd/radio';
import { NzSpinModule } from 'ng-zorro-antd/spin';
import { NzTableModule } from 'ng-zorro-antd/table';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { RemoteMarketItem, RemoteMarketService } from './remote-market.service';

@Component({
  selector: 'app-remote-market',
  standalone: true,
  imports: [
    PageHeaderComponent,
    FormsModule,
    NzButtonModule,
    NzInputModule,
    NzRadioModule,
    NzSpinModule,
    NzTableModule,
    NzTagModule
  ],
  template: `
    <app-page-header></app-page-header>
    <section class="page-content">
      <div class="remote-market-toolbar">
        <nz-radio-group [(ngModel)]="typeFilter" (ngModelChange)="load()">
          <label nz-radio-button nzValue="">全部</label>
          <label nz-radio-button nzValue="template">模板</label>
          <label nz-radio-button nzValue="plugin">插件</label>
        </nz-radio-group>
        <input
          nz-input
          class="remote-market-search"
          placeholder="搜索名称、摘要、标签"
          [(ngModel)]="keyword"
          (keyup.enter)="load()"
        />
        <button nz-button type="button" (click)="load()">搜索</button>
        <button nz-button type="button" [nzLoading]="syncing()" (click)="sync()">刷新索引</button>
      </div>
      <nz-spin [nzSpinning]="loading()">
        <nz-table nzSize="small" [nzData]="items()" [nzFrontPagination]="true" [nzPageSize]="20">
          <thead>
            <tr>
              <th>名称</th>
              <th nzWidth="80px">类型</th>
              <th nzWidth="90px">版本</th>
              <th>摘要</th>
              <th nzWidth="100px">分类</th>
              <th nzWidth="90px">兼容性</th>
              <th nzWidth="80px">操作</th>
            </tr>
          </thead>
          <tbody>
            @for (row of items(); track row.slug + row.version + row.sourceId) {
              <tr>
                <td>{{ row.name }}</td>
                <td>
                  @if (row.type === 'template') {
                    <nz-tag nzColor="blue">模板</nz-tag>
                  } @else {
                    <nz-tag nzColor="purple">插件</nz-tag>
                  }
                </td>
                <td>{{ row.version }}</td>
                <td>{{ row.summary || '—' }}</td>
                <td>{{ row.category || '—' }}</td>
                <td>
                  @if (row.kiwiCompatible === false) {
                    <nz-tag nzColor="error">不兼容</nz-tag>
                  } @else if (row.missingComponentKeys?.length) {
                    <nz-tag nzColor="warning">缺组件</nz-tag>
                  } @else {
                    <nz-tag nzColor="success">可用</nz-tag>
                  }
                </td>
                <td>
                  <button nz-button nzType="link" nzSize="small" type="button" (click)="openDetail(row)">详情</button>
                </td>
              </tr>
            }
          </tbody>
        </nz-table>
      </nz-spin>
    </section>
  `,
  styles: [
    `
      .remote-market-toolbar {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        align-items: center;
        margin-bottom: 16px;
      }
      .remote-market-search {
        width: 240px;
      }
    `
  ]
})
export class RemoteMarket implements OnInit {
  private readonly marketService = inject(RemoteMarketService);
  private readonly router = inject(Router);
  private readonly message = inject(NzMessageService);

  readonly items = signal<RemoteMarketItem[]>([]);
  readonly loading = signal(false);
  readonly syncing = signal(false);

  typeFilter = '';
  keyword = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.marketService
      .list({
        type: this.typeFilter || undefined,
        keyword: this.keyword.trim() || undefined
      })
      .subscribe({
        next: list => {
          this.items.set(list);
          this.loading.set(false);
        },
        error: () => this.loading.set(false)
      });
  }

  sync(): void {
    this.syncing.set(true);
    this.marketService.sync().subscribe({
      next: () => {
        this.syncing.set(false);
        this.load();
      },
      error: () => this.syncing.set(false)
    });
  }

  openDetail(row: RemoteMarketItem): void {
    void this.router.navigate(['/bpm/remote-market', row.slug, row.version], {
      queryParams: row.sourceId ? { sourceId: row.sourceId } : undefined
    });
  }
}
