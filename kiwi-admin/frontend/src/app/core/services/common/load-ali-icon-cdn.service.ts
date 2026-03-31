import { inject, Injectable } from '@angular/core';

import { NzIconService } from 'ng-zorro-antd/icon';

// 获取阿里图标库
@Injectable({
  providedIn: 'root'
})
export class LoadAliIconCdnService {
  private iconService = inject(NzIconService);

  load(): void {
    // 这个js你要自己去阿里图标库的官网自己生成
    this.iconService.fetchFromIconfont({
      scriptUrl: 'https://at.alicdn.com/t/c/font_5129484_oq20mdndc4i.js'
    });
  }
}
