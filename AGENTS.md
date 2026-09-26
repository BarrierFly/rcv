# AGENTS.md

**中文** · **English**

本文件面向在此仓库中工作的自动化代理 / 开发者。

This file is for automated agents and developers working in this repository.

## 项目 / Project

- Mod id `rcv`，包名 `dev.rcvmod.rcv`。
- Fabric，Minecraft `1.21.11`，官方 **Mojmap**，Java 21。
- 构建插件 `net.fabricmc.fabric-loom-remap`（`gradle.properties` 中的 `loom_version`）。
- 依赖：`fabric-api:0.141.6+1.21.11`（`gradle.properties`）。

## 构建 / 测试命令 / Build & test

在仓库根目录（本目录）运行：

```bash
./gradlew build          # 编译 + 单测 + remapJar -> build/libs/
./gradlew build --console=plain
./gradlew test           # 仅核心模型单测
./gradlew runClient      # 开发客户端
./gradlew runServer      # 开发服务端
```

Windows PowerShell 使用 `.\gradlew.bat`。

- 不要改动 Loom / Fabric API / loader 版本，除非同时更新 `gradle.properties` 并确认可解析。
- 版本可通过 Fabric meta 查询：<https://fabricmc.net/develop>。
- 构建产物为 `build/libs/rcv-<version>.jar`（以及 `-sources.jar`）。

## 代码结构 / Layout

```
src/main/java/dev/rcvmod/rcv/
├── core/      图模型与遍历（EdgeType / ConnectionGraph / ConnectionEngine / WorldView）
├── mc/        Level -> WorldView 适配
├── command/   服务端 /rcv 与共享解析
├── client/    渲染、魔杖、HUD、客户端 /rcv
└── config/    JSON 配置（客户端 + 服务端热重载）
src/main/resources/  fabric.mod.json、lang
src/test/java/       核心模型单测（不依赖 Minecraft 启动）
```

## 添加连接类型的步骤 / Adding a connection type

1. 在 `core/EdgeType.java` 增加枚举常量，并设置优先级与 `id`。
2. 在 `core/ConnectionEngine.java` 的 `outgoing(...)` 中生成该类型的 `Candidate`；
   若为无向类型，使用 `addUndirected(...)`。
3. 在 `client/RcvGraphRenderer`（配色）与 `config/RcvConfig.defaultColor` 增加颜色。
4. 在 `assets/rcv/lang/{en_us,zh_cn}.json` 增加 `rcv.edge.<id>`。
5. 补充 `src/test/java` 中的模型测试。

## 约定 / Conventions

- 信号方向严格遵守 §3.1：`WorldView.weakSignalTo(emitter, dirFromEmitterToReceiver)` 内部翻转后调用 `SignalGetter`。
- BFS 只在 `WorldView.isLoaded` 且（若配置）区域内的位置扩展；同一 `(from,to)` 只保留优先级最高的一条边。
- 不新增 mixin（本设计静态计算，不需要）。
- 提交前务必让 `./gradlew build` 通过。
