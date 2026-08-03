import { Component, inject, OnInit, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzDescriptionsModule } from 'ng-zorro-antd/descriptions';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { RemoteMarketItemDetail, RemoteMarketService } from './remote-market.service';

@Component({
  selector: 'app-remote-market-detail',
  standalone: true,
  imports: [
    PageHeaderComponent,
    FormsModule,
    JsonPipe,
    NzButtonModule,
    NzCardModule,
    NzDescriptionsModule,
    NzInputModule,
    NzTagModule
  ],
  template: `
    <app-page-header></app-page-header>
    <section class="page-content">
      @if (detail()) {
        <nz-card [nzTitle]="detail()!.name">
          <p>{{ detail()!.summary }}</p>
          <nz-descriptions nzBordered nzSize="small" class="m-b-16">
            <nz-descriptions-item nzTitle="类型">{{ detail()!.type }}</nz-descriptions-item>
            <nz-descriptions-item nzTitle="Slug">{{ detail()!.slug }}</nz-descriptions-item>
            <nz-descriptions-item nzTitle="版本">{{ detail()!.version }}</nz-descriptions-item>
            <nz-descriptions-item nzTitle="分类">{{ detail()!.category || '—' }}</nz-descriptions-item>
            <nz-descriptions-item nzTitle="来源">{{ detail()!.sourceName || detail()!.sourceId }}</nz-descriptions-item>
            <nz-descriptions-item nzTitle="最低 Kiwi">{{ detail()!.kiwiMinVersion || '—' }}</nz-descriptions-item>
            <nz-descriptions-item nzTitle="SHA-256" [nzSpan]="3">
              <code class="sha-cell">{{ detail()!.sha256 }}</code>
            </nz-descriptions-item>
          </nz-descriptions>
          @if (detail()!.tags?.length) {
            <p>
              @for (t of detail()!.tags; track t) {
                <nz-tag>{{ t }}</nz-tag>
              }
            </p>
          }
          @if (detail()!.type === 'template' && detail()!.requiredComponentKeys?.length) {
            <h4>依赖组件</h4>
            <p>
              @for (k of detail()!.requiredComponentKeys; track k) {
                <nz-tag [nzColor]="detail()!.missingComponentKeys?.includes(k) ? 'error' : 'default'">{{ k }}</nz-tag>
              }
            </p>
          }
          @if (detail()!.type === 'plugin' && detail()!.componentKeys?.length) {
            <h4>提供组件</h4>
            <p>
              @for (k of detail()!.componentKeys; track k) {
                <nz-tag>{{ k }}</nz-tag>
              }
            </p>
          }
          @if (detail()!.manifest) {
            <h4>Manifest</h4>
            <pre class="manifest-pre">{{ detail()!.manifest | json }}</pre>
          }
          <div class="m-t-16">
            @if (detail()!.type === 'template') {
              <input nz-input class="project-name-input" placeholder="新项目名称（可选）" [(ngModel)]="projectName" />
              <button
                class="m-l-8"
                nz-button
                nzType="primary"
                type="button"
                [disabled]="!canInstall()"
                (click)="installTemplate()"
              >
                安装为新项目
              </button>
            } @else {
              <button nz-button nzType="primary" type="button" [disabled]="!canInstall()" (click)="installPlugin()">
                下载并安装插件
              </button>
            }
            <button class="m-l-8" nz-button type="button" (click)="goBack()">返回列表</button>
          </div>
          @if (!canInstall()) {
            <p class="install-hint">版本不兼容或缺少依赖组件时无法安装。可先安装所需插件后重试。</p>
          }
        </nz-card>
      }
    </section>
  `,
  styles: [
    `
      .sha-cell {
        word-break: break-all;
        font-size: 12px;
      }
      .manifest-pre {
        background: #fafafa;
        padding: 12px;
        border-radius: 4px;
        max-height: 320px;
        overflow: auto;
      }
      .project-name-input {
        width: 280px;
      }
      .install-hint {
        margin-top: 12px;
        color: rgba(0, 0, 0, 0.45);
      }
    `
  ]
})
export class RemoteMarketDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly marketService = inject(RemoteMarketService);
  private readonly modalWrap = inject(NzModalWrapService);
  private readonly message = inject(NzMessageService);

  readonly detail = signal<RemoteMarketItemDetail | null>(null);
  projectName = '';
  private slug = '';
  private version = '';
  private sourceId = '';

  ngOnInit(): void {
    this.slug = this.route.snapshot.paramMap.get('slug') ?? '';
    this.version = this.route.snapshot.paramMap.get('version') ?? '';
    this.sourceId = this.route.snapshot.queryParamMap.get('sourceId') ?? '';
    if (this.slug && this.version) {
      this.load();
    }
  }

  load(): void {
    this.marketService.get(this.slug, this.version, this.sourceId || undefined).subscribe(d => this.detail.set(d));
  }

  canInstall(): boolean {
    const d = this.detail();
    if (!d) {
      return false;
    }
    if (d.kiwiCompatible === false) {
      return false;
    }
    return !d.missingComponentKeys?.length;
  }

  installTemplate(): void {
    const d = this.detail();
    if (!d || !this.canInstall()) {
      return;
    }
    this.modalWrap.confirm({
      nzTitle: '安装远程模板',
      nzContent: `将下载并安装「${d.name}」v${d.version} 为新项目，是否继续？`,
      nzOnOk: () =>
        this.marketService
          .installTemplate(
            this.slug,
            this.version,
            this.projectName.trim() ? { projectName: this.projectName.trim() } : undefined,
            this.sourceId || undefined
          )
          .subscribe({
            next: res => {
              if (res.projectId) {
                void this.router.navigate(['/bpm/project']);
              }
            }
          })
    });
  }

  installPlugin(): void {
    const d = this.detail();
    if (!d || !this.canInstall()) {
      return;
    }
    this.modalWrap.confirm({
      nzTitle: '安装远程插件',
      nzContent: `将下载并安装插件「${d.name}」v${d.version}，安装后请刷新组件库。是否继续？`,
      nzOnOk: () =>
        this.marketService.installPlugin(this.slug, this.version, this.sourceId || undefined).subscribe({
          next: () => this.message.success('插件已安装，组件库将自动刷新')
        })
    });
  }

  goBack(): void {
    void this.router.navigate(['/bpm/remote-market']);
  }
}
