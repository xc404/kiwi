import { TestBed } from '@angular/core/testing';

import { BpmDesignerContextService } from '../../bpm-designer-context.service';
import { BpmProjectEnvCatalogService } from '../../env/bpm-project-env-catalog.service';
import type { ExpressionVariable } from '../expression-variable';
import { BpmProjectEnvVariableProvider } from './bpm-project-env-variable-provider';

describe('BpmProjectEnvVariableProvider', () => {
  let provider: BpmProjectEnvVariableProvider;
  let designerContext: BpmDesignerContextService;
  let catalogService: jasmine.SpyObj<BpmProjectEnvCatalogService>;

  beforeEach(() => {
    catalogService = jasmine.createSpyObj('BpmProjectEnvCatalogService', ['getCatalog']);
    TestBed.configureTestingModule({
      providers: [
        BpmProjectEnvVariableProvider,
        BpmDesignerContextService,
        { provide: BpmProjectEnvCatalogService, useValue: catalogService }
      ]
    });
    provider = TestBed.inject(BpmProjectEnvVariableProvider);
    designerContext = TestBed.inject(BpmDesignerContextService);
  });

  it('无 projectId 时不贡献变量', () => {
    designerContext.projectId.set(null);
    const collected: ExpressionVariable[] = [];
    provider.provide({
      bpmnModeler: {} as never,
      currentElement: {} as never,
      addVariable: v => collected.push(v),
      addMethod: () => undefined
    });
    expect(collected).toEqual([]);
  });

  it('贡献项目环境变量且 kind 为 projectEnv', () => {
    designerContext.projectId.set('proj-1');
    catalogService.getCatalog.and.returnValue(
      new Map([
        ['API_URL', { key: 'API_URL', description: '根地址', encrypted: false }],
        ['API_KEY', { key: 'API_KEY', encrypted: true }]
      ])
    );
    const collected: ExpressionVariable[] = [];
    provider.provide({
      bpmnModeler: {} as never,
      currentElement: {} as never,
      addVariable: v => collected.push(v),
      addMethod: () => undefined
    });
    expect(collected).toEqual([
      { key: 'API_URL', kind: 'projectEnv', name: '根地址' },
      { key: 'API_KEY', kind: 'projectEnv', name: '（加密）' }
    ]);
  });
});
