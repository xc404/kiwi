## 1. Shell

- [x] 1.1 新增 `ShellActivityBehaviorTest`（echo 成功、缺少 command、convertStreamToStr）

## 2. File

- [x] 2.1 新增 `FileReadActivityTest`（读写、maxBytes、非法路径、文件不存在）
- [x] 2.2 新增 `FileWriteActivityTest`（覆盖写入、append、createDirectories）

## 3. Mongo

- [x] 3.1 新增 `MongoActivityTest`（findOne、insert、count、非法 JSON、空 collection）

## 4. 验证

- [x] 4.1 `mvn -pl kiwi-bpmn/kiwi-bpmn-component -am test` 通过
- [x] 4.2 更新路线图 plan 中 `bpm-test-matrix` 为 completed
