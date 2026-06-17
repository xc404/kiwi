## Why

`cryoems-bpm` 作为 kiwi monorepo 子模块与通用平台代码耦合，独立发版与权限边界不清。需物理迁出为独立 Git 仓库，kiwi 通过 Maven 构件（GitHub Packages + 本地 sibling profile）依赖。

## What Changes

- 将 `cryoems-bpm` 移至 `d:\Projects\cryoems-bpm` 并 `git init`
- 重写为独立 Maven 根项目（`${kiwi.version}` 固定依赖 kiwi 模块）
- kiwi 移除 reactor module；`cryoems-bpm.version` + GitHub Packages 拉取 + `cryoems-bpm-local` profile
- `kiwi-admin/backend` 依赖改为 `${cryoems-bpm.version}`
- GitHub Packages 发布与 CI、`settings.xml` 认证

## Capabilities

### New Capabilities

- （无 main spec。）

### Modified Capabilities

- （无。）

## Impact

- `cryoems-bpm` 独立仓库、`kiwi/pom.xml`、`kiwi-admin/backend/pom.xml`
- Maven `settings.xml`、GitHub Actions（cryoems-bpm 侧）

## 非目标

- 修改 `openspec/` 历史 change 中的路径引用
- Java 包名 `com.kiwi.cryoems.bpm` 保持不变
- kiwi 全模块发布 GitHub Packages（建议后续单独 change）
