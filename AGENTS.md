# AGENTS.md

**中文** · **English**

本文件面向在此仓库中工作的自动化代理 / 开发者。

This file is for automated agents and developers working in this repository.

## 项目 / Project

- Mod id `rcv`，包名 `dev.rcvmod.rcv`。
- Fabric；目标版本 **1.19.4 / 1.20.1 / 1.21.1 / 1.21.10 / 1.21.11 / 26.3**；官方 **Mojmap**；
  Java 17（1.19.4 / 1.20.1）、21（1.21.x）、25（26.x）。
- 多版本构建：根 `build.gradle` + `common.gradle` + `versions/<v>/gradle.properties`；
  共享源码 `src/main`，版本差异 `src/versions/<v>/java`。

## 构建 / 测试命令 / Build & test

在仓库根目录（本目录）运行：

```bash
./gradlew buildAndGather          # 构建全部版本，汇总 jar 到 build/libs
./gradlew :1.21.11:build          # 单版本构建
./gradlew :1.19.4:test            # 单版本核心模型单测
./gradlew :26.3:build             # 26.x（unobfuscated，JDK 25 toolchain）
./gradlew :1.21.11:runClient      # 开发客户端（runDir = 根目录 run/）
```

Windows PowerShell 使用 `.\gradlew.bat`。

- 版本依赖写在 `versions/<v>/gradle.properties`：`minecraft_version`、`fabric_api_version`、
  `cloth_config_version`、`modmenu_version`、`minecraft_dependency`、`java_version`。
- `java_version` 同时决定 `options.release`、Gradle toolchain 与 `fabric.mod.json` 的
  `"java": ">=<n>"` 依赖。
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
               ClientCompat / ServerCompat / RenderCompat / HudCompat / BlockCompat
src/main/resources/  fabric.mod.json、lang
src/versions/<v>/java/dev/rcvmod/rcv/version/  版本差异（渲染 API、权限、客户端命令、HUD、方块家族）
src/test/java/       核心模型单测（不依赖 Minecraft 启动）
```

### 版本差异点 / Version-specific surface

- `RenderCompat`：1.19.4 / 1.20.1 / 1.21.1 用顶层 `WorldRenderEvents` + `RenderType.debugQuads()`；
  1.21.10 / 1.21.11 用 `.world.WorldRenderEvents`（1.21.11 换 `RenderTypes.linesTranslucent()`）；
  26.3 用 `.level.LevelRenderEvents` + `SubmitNodeCollector`。
  顶点写入 API 也分两代：≤1.20.1 是 `vertex(Matrix4f, ..).color(int).endVertex()`，
  ≥1.21.1 是 `addVertex(Pose, ..).setColor(int)`（无 `endVertex`）。
- `ServerCompat`：1.19.4 / 1.20.1 / 1.21.1 / 1.21.10 用 `source.hasPermission(2)`；
  1.21.11 / 26.3 用 `permissions().hasPermission(...)`；
  `dust(int)` 兼容 `DustParticleOptions` 构造签名；
  `sendSuccess(...)` 抹平 1.19.4 只有 `sendSuccess(Component, boolean)`、
  1.20.1+ 只有 `sendSuccess(Supplier<Component>, boolean)` 的差异。
- `ClientCompat`：命令字面量（`ClientCommandManager` vs 26.3 `ClientCommands`）、
  世界访问（`getWorld()` vs 26.3 `getLevel()`）、打开界面（`setScreen` vs 26.3 `setScreenAndShow`）、
  玩家消息（`displayClientMessage` vs 26.3 `sendSystemMessage`）。
- `HudCompat`：1.19.4 的 `HudRenderCallback` 只给 `PoseStack`，需用 `Font#drawShadow`；
  1.20.1 / 1.21.x 给 `DrawContext` / `GuiGraphics`，用 `drawString`；
  26.3 改用抽取式 API —— `HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, ...)`
  + `GuiGraphicsExtractor#text`。挂在原版 `CHAT` 之后是为了继承 `Hud#isHidden`
  （按 F1 时和 1.21.x 的 `HudRenderCallback` 一样隐藏）；`addLast` 会挂在字幕层，
  而字幕层在 HUD 隐藏时仍会被 `deferredSubtitles` 调用。
- `BlockCompat`：`crafter` 与铜灯泡（1.21 才有）、校频幽匿感测体（1.20 才有）在旧版本不存在，
  由 `ComponentCatalog` 通过 `BlockCompat` 查询，缺失时返回 `false`。
- 共享的 `client/RcvHud` 只产出 `List<Line(x, y, text, color)>`，不含任何绘图 API，
  绘制完全交给各版本的 `HudCompat`（26.x 已用 `GuiGraphicsExtractor` 取代 `GuiGraphics`）。

## 添加连接类型的步骤 / Adding a connection type

1. 在 `core/EdgeType.java` 增加枚举常量，并设置优先级与 `id`。
2. 在 `core/ConnectionEngine.java` 的 `outgoing(...)` 中生成该类型的 `Candidate`；
   若为无向类型，使用 `addUndirected(...)`。
3. 在 `client/GraphGeometry` 的配色与 `config/RcvConfig.defaultColor` 增加颜色。
4. 在 `assets/rcv/lang/{en_us,zh_cn}.json` 增加 `rcv.edge.<id>`。
5. 补充 `src/test/java` 中的模型测试。

## 新增版本 / Adding a version

1. `settings.gradle` 的版本列表加入新版本。
2. 新建 `versions/<v>/gradle.properties`（依赖版本 + `java_version`）。
3. 新建 `src/versions/<v>/java/dev/rcvmod/rcv/version/` 下五个兼容类。
4. `./gradlew :<v>:build` 通过。

## 约定 / Conventions

- 信号方向严格遵守 §3.1：`WorldView.weakSignalTo(emitter, dirFromEmitterToReceiver)` 内部翻转后调用 `SignalGetter`。
- BFS 只在 `WorldView.isLoaded` 且（若配置）区域内的位置扩展；同一 `(from,to)` 只保留优先级最高的一条边。
- 不新增 mixin（本设计静态计算，不需要）。若某版本的 Fabric HUD 回调拿不到绘图对象，
  优先找该版本 API 自身的替代（如 1.19.4 用 `Font#drawShadow`），而不是加 mixin。
- 新增方块家族判定前先用 guardian 的对应版本提交核对类是否存在
  （`mojmap/vineflower` 最新提交 = 26.3；`parchment/vineflower` 历史提交 = 1.16.5 ~ 1.21.11）。
- 改动共享代码后，必须让 **全部** `:1.19.4:build` / `:1.20.1:build` / `:1.21.1:build` /
  `:1.21.10:build` / `:1.21.11:build` / `:26.3:build` 通过。
