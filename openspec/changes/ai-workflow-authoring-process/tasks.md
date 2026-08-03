## 1. Foundations

- [x] 1.1 Add config flags/properties (`kiwi.ai.workflow-authoring.enabled`, repair max rounds, catalog Top-N)
- [x] 1.2 Define process variable contract (scenario, targetProcessId, catalogJson, planIr, candidateXml, issues, dispatchCode, repairRound, pluginHint)
- [x] 1.3 Scaffold packages/classes under `com.kiwi.project.ai` for authoring (catalog, validate, process bridge)

## 2. Catalog and extraction

- [x] 2.1 Implement scenario keyword/tag extraction (bounded LLM and/or rules)
- [x] 2.2 Implement `CatalogContextBuilder`: installed components + template pack summaries + installable-not-installed candidates
- [x] 2.3 Mark catalog entries `installed` vs `available_to_install`; truncate Top-N; unit tests for merge/score

## 3. Validator

- [x] 3.1 Implement `BpmAiWorkflowValidator` L0 (XML/definitions) reusing/extending `BpmDesignerXmlValidator`
- [x] 3.2 Add L1 structural checks and L2 componentId resolve + missing-plugin detection + required params
- [x] 3.3 Map issues to aggregate `dispatchCode` (`PASS`/`REPAIR`/`INSTALL`/`ASK`); unit tests with fixture BPMN

## 4. Kiwi authoring process definition

- [x] 4.1 Model BPMN `kiwi_ai_workflow_authoring` (extract → catalog → plan → generate → validate → gate → repair/install/ask/preview → save)
- [x] 4.2 Deploy as classpath seed (or documented deploy step) and verify process definition present at startup when feature enabled
- [x] 4.3 Wire repair loop bound by `repairRound` and install/ask/preview User Tasks

## 5. Delegates and LLM steps

- [x] 5.1 Implement Service Task delegates: extract, catalog, plan, generate/repair, validate, install plugin, save target process
- [x] 5.2 Constrain plan/generate prompts to injected `catalogJson`; set `requiresInstall` for installable ids
- [x] 5.3 Ensure save delegate only runs after preview confirmation variable is true

## 6. Designer / assistant bridge

- [x] 6.1 Start or correlate authoring process from `AiAssistantService` / designer when scenario authoring is enabled
- [x] 6.2 Expose APIs or task payloads for stage status, preview XML, plugin install confirmation, ask-user
- [x] 6.3 Frontend: show stage in `bpm-ai-chat`; preview import without auto-save; confirm save / confirm install / decline paths
- [x] 6.4 Feature flag off → fallback to previous assistant behavior

## 7. Hardening and docs

- [ ] 7.1 Integration test or manual script: scenario → catalog → invalid XML repair → preview → save
- [ ] 7.2 Integration path: missing plugin → user confirm install → re-validate → preview
- [x] 7.3 Update plan file status / brief NOTES in change if behavior differs from first model
- [ ] 7.4 Ready for `/opsx:archive` after verification
