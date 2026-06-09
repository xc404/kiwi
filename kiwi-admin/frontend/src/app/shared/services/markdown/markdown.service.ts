import { Injectable } from '@angular/core';

import DOMPurify from 'dompurify';
import hljs from 'highlight.js/lib/core';
import bash from 'highlight.js/lib/languages/bash';
import css from 'highlight.js/lib/languages/css';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';
import json from 'highlight.js/lib/languages/json';
import python from 'highlight.js/lib/languages/python';
import sql from 'highlight.js/lib/languages/sql';
import typescript from 'highlight.js/lib/languages/typescript';
import xml from 'highlight.js/lib/languages/xml';
import yaml from 'highlight.js/lib/languages/yaml';
import { Marked } from 'marked';
import { markedHighlight } from 'marked-highlight';

const HIGHLIGHT_LANGUAGES: Array<[string, Parameters<typeof hljs.registerLanguage>[1]]> = [
  ['bash', bash],
  ['css', css],
  ['html', xml],
  ['java', java],
  ['javascript', javascript],
  ['json', json],
  ['python', python],
  ['sql', sql],
  ['typescript', typescript],
  ['xml', xml],
  ['yaml', yaml]
];

@Injectable({
  providedIn: 'root'
})
export class MarkdownService {
  private readonly marked: Marked;

  constructor() {
    for (const [name, lang] of HIGHLIGHT_LANGUAGES) {
      hljs.registerLanguage(name, lang);
    }

    this.marked = new Marked(
      {
        gfm: true,
        breaks: true
      },
      markedHighlight({
        emptyLangClass: 'hljs',
        langPrefix: 'hljs language-',
        highlight: (code, lang) => {
          const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext';
          return hljs.highlight(code, { language }).value;
        }
      }),
      {
        renderer: {
          link({ href, title, tokens }) {
            const text = this.parser.parseInline(tokens);
            const titleAttr = title ? ` title="${title}"` : '';
            return `<a href="${href}" target="_blank" rel="noopener noreferrer"${titleAttr}>${text}</a>`;
          }
        }
      }
    );
  }

  render(markdown: string): string {
    if (!markdown?.trim()) {
      return '';
    }
    const html = this.marked.parse(markdown, { async: false });
    return DOMPurify.sanitize(html, {
      ADD_ATTR: ['target', 'rel']
    });
  }
}
