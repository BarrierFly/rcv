# AGENTS.md

**中文** · **English**

本文件面向在此仓库中工作的自动化代理 / 开发者。

This file is for automated agents and developers working in this repository.

## 项目 / Project

- Mod id `rcv`，包名 `dev.rcvmod.rcv`。
- Fabric；目标版本 **1.21.1 / 1.21.10 / 1.21.11 / 26.3**；官方 **Mojmap**；Java 21（26.x 为 Java 25）。
- 多版本构建：根 `build.gradle` + `common.gradle` + `versions/<v>/gradle.properties`；
  共享源码 `src/main`，版本差异 `src/versions/<v>/java`。

## 构建 / 测试命令 / Build & test

在仓库根目录（本目录）运行：

```bash
./gradlew buildAndGather          # 构建全部版本，汇总 jar 到 build/libs
./gradlew :1.21.11:build          # 单版本构建
./gradlew :1.21.1:test            # 单版本核心模型单测
./gradlew :26.3:build             # 26.x（unobfuscated，JDK 25 toolchain）
./gradlew :1.21.11:runClient      # 开发客户端（runDir = 根目录 run/）
```

Windows PowerShell 使用 `.\gradlew.bat`。

- 版本依赖写在 `versions/<v>/gradle.properties`：`minecraft_version`、`fabric_api_version`、
  `cloth_config_version`、`modmenu_version`、`minecraft_dependency`。
- 构建产物：`build/libs/rcv-mc<version>-<modversion>.jar`。
- 26.x 需要 JDK 25，通过 `settings.gradle` 的 foojay toolchain 解析器自动获取。

## 代码结构 / Layout

```
src/main/java/dev/rcvmod/rcv/
├── core/      图模型与遍历（EdgeType / ConnectionGraph / ConnectionEngine / WorldView / ComponentCatalog）
├── mc/        Level -> WorldView 适配（PistonStructureResolver / RailState）
├── command/   服务端 /rcv 与共享解析
├── client/    渲染几何（GraphGeometry）、HUD 内容、魔杖、客户端 /rcv、配置界面
├── config/    JSON 配置（客户端 + 服务端热重载）
└── version/   ← 仅存在于 src/versions/<v>/java，按版本提供差异实现
               ClientCompat / ServerCompat / RenderCompat / HudCompat
src/main/resources/  fabric.mod.json、lang
src/versions/<v>/java/dev/rcvmod/rcv/version/  版本差异（渲染 API、权限、客户端命令、HUD）
src/test/java/       核心模型单测（不依赖 Minecraft 启动）
```

### 版本差异点 / Version-specific surface

- `RenderCompat`：1.21.1 用顶层 `WorldRenderEvents` + `RenderType.lines()` + `RenderSystem.lineWidth`；
  1.21.10 用 `.world.WorldRenderEvents` + `RenderType.lines()`；1.21.11 用 `.world` + `RenderTypes.linesTranslucent()` + `setLineWidth`；
  26.3 用 `.level.LevelRenderEvents` + `SubmitNodeCollector`。
- `ServerCompat`：1.21.1 / 1.21.10 用 `source.hasPermission(2)`；1.21.11 / 26.3 用 `permissions().hasPermission(...)`；
  `dust(int)` 兼容 `DustParticleOptions` 构造签名。
- `ClientCompat`：命令字面量（`ClientCommandManager` vs 26.3 `ClientCommands`）、
  世界访问（`getWorld()` vs 26.3 `getLevel()`）、打开界面（`setScreen` vs 26.3 `setScreenAndShow`）、
  玩家消息（`displayClientMessage` vs 26.3 `sendSystemMessage`）。
- `HudCompat`：1.21.x 用 `HudRenderCallback`；26.3 暂为 no-op（共享 `RcvHud` 在 26.3 被
  `common.gradle` 排除，因为 `GuiGraphics` 已被 `GuiGraphicsExtractor` 取代）。

## 添加连接类型的步骤 / Adding a connection type

1. 在 `core/EdgeType.java` 增加枚举常量，并设置优先级与 `id`。
2. 在 `core/ConnectionEngine.java` 的 `outgoing(...)` 中生成该类型的 `Candidate`；
   若为无向类型，使用 `addUndirected(...)`。
3. 在 `client/GraphGeometry` 的配色与 `config/RcvConfig.defaultColor` 增加颜色。
4. 在 `assets/rcv/lang/{en_us,zh_cn}.json` 增加 `rcv.edge.<id>`。
5. 补充 `src/test/java` 中的模型测试。

## 新增版本 / Adding a version

1. `settings.gradle` 的版本列表加入新版本。
2. 新建 `versions/<v>/gradle.properties`（依赖版本）。
3. 新建 `src/versions/<v>/java/dev/rcvmod/rcv/version/` 下四个兼容类。
4. `./gradlew :<v>:build` 通过。

## 约定 / Conventions

- 信号方向严格遵守 §3.1：`WorldView.weakSignalTo(emitter, dirFromEmitterToReceiver)` 内部翻转后调用 `SignalGetter`。
- BFS 只在 `WorldView.isLoaded` 且（若配置）区域内的位置扩展；同一 `(from,to)` 只保留优先级最高的一条边。
- 不新增 mixin（本设计静态计算，不需要）。
- 改动共享代码后，必须让 **全部** `:1.21.1:build` / `:1.21.10:build` / `:1.21.11:build` / `:26.3:build` 通过。
