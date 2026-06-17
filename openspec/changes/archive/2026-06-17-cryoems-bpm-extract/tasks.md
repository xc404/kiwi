# Tasks

## 1. 物理迁移

- [x] 移动 `cryoems-bpm` 至 `d:\Projects\cryoems-bpm`；kiwi 仓库移除该目录

## 2. 独立 Maven 与 kiwi 配置

- [x] `cryoems-bpm/pom.xml` 独立根项目 + `${kiwi.version}` 依赖
- [ ] kiwi `pom.xml`：`cryoems-bpm-local` profile、GitHub Packages `repositories`
- [ ] `kiwi-admin/backend/pom.xml`：`cryoems-bpm` 依赖 `${cryoems-bpm.version}`（当前未声明）

## 3. 发布与验证

- [ ] Maven `settings.xml` + cryoems-bpm GitHub Actions deploy
- [ ] 验证：独立 `mvn test`、`-Pcryoems-bpm-local` compile、Packages 拉取
