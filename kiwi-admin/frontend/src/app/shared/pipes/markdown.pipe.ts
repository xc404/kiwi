import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

import { MarkdownService } from '@shared/services/markdown/markdown.service';

@Pipe({
  name: 'markdown',
  standalone: true
})
export class MarkdownPipe implements PipeTransform {
  private markdownService = inject(MarkdownService);
  private domSanitizer = inject(DomSanitizer);

  transform(value: string | null | undefined): SafeHtml {
    if (!value?.trim()) {
      return '';
    }
    const html = this.markdownService.render(value);
    return this.domSanitizer.bypassSecurityTrustHtml(html);
  }
}
