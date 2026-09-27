# RCV — Redstone Connection Visualized

**中文** | [English](README.md)

RCV 可视化任意红石元件在**拓扑意义上**的上游输入（`in`）与下游输出（`out`）连接图。
它只判断“是否存在连接”，**不做时序 / 信号强度仿真**，也不做故障检测。

RCV visualises the **topological** upstream inputs (`in`) and downstream outputs (`out`) of any redstone
component. It only answers "is there a connection", and deliberately does **not** simulate timing or
signal strength, nor detect faults.

- 平台 / Platform: **Fabric**, Java **21**（`26.x` 需 Java **25**）
- 支持版本 / Versions: **1.21.1 / 1.21.10 / 1.21.11 / 26.3**（见下方构建）
- Mod id: `rcv`；包名 `dev.rcvmod.rcv`
- 许可 / License: **LGPL-3.0**（见 [LICENSE](LICENSE)）

## 功能 / Features

- 13 类连接 / 13 connection types:
  激活 `direct_activation`、电路 `circuit`（含比较器侧输入 `comparator_side`）、模拟读数 `analog`、
  充能 `charge`、半连接 / QC `half`、绊线 `tripwire`、活塞 `piston`、门两半 `door_pair`、
  轨道 `rail`、形状 `shape`、距离 `distance`、NC 更新 `nc`、PP 更新 `pp`。
- 单次请求计算可达子图，带深度、区域裁剪、节点 / 边上限，结果稳定排序。
- 世界空间线框叠加：节点立方体、边线、箭头、via 菱形路径；按类型配色，深度越深越淡。
- 魔杖取点与区域选区（默认紫色染料）。
- 配置 GUI：Cloth Config，两个入口 **ModMenu** 与 **`/rcv config`**。
- 服务端兜底（`S only`）：原版客户端收到聊天汇总 + 粒子。
- 客户端 / 服务端 JSON 配置，服务端命令支持 `/rcv reload` 热重载。
- 色盲友好备用调色板（Okabe-Ito）。

### 三种连接方式说明 / Notes on the added types

- **轨道 `rail`**：动力铁轨 / 激活铁轨从信号源起单向延伸，最多 8 格；包含斜向与曲线连接。
- **形状 `shape`**：按“A 的易变状态一变、B 的形状就跟着变”判定，双向连接：门/活板门
  （`open` 翻转其面支撑）↔ 相邻栅栏 / 铁栏杆 / 墙，以及相邻墙↔墙（`UP` / 连接柱依赖）。
  静态连接（栅栏↔栅栏、铁栏杆↔墙、满方块面支撑、钟、朝向对齐）不显示。
- **距离 `distance`**：树叶之间双向；脚手架水平、垂直双向，并含脚手架 ↔ 下方支撑块
  （如关闭的活板门）依赖。
- **PP 默认档 = 仅侦测器 `observer_only`**：只显示侦测器侦测其正前方方块；可在配置中改为 `all`。

## 命令 / Commands

```
/rcv in  <x> <y> <z> [depth] [options]
/rcv out <x> <y> <z> [depth] [options]
/rcv clear
/rcv refresh
/rcv reload        (服务端 / server)
/rcv types
```

- 坐标支持 `~` 相对坐标；`depth` 省略时默认 16。
- `options`：`--no-<type>`（如 `--no-nc --no-pp`）与 `--region <x1> <y1> <z1> <x2> <y2> <z2>`（绝对整数）。

客户端额外子命令 / extra client subcommands:

```
/rcv config                 # 打开 Cloth Config 配置界面
/rcv mode in|out
/rcv depth <n>
/rcv wand <true|false>
/rcv region mode <true|false>
/rcv region clear
/rcv colorblind <true|false>
```

## 魔杖 / Wand

- 手持**紫色染料**右键方块 = 以此方块为起点并按当前模式查询。
- `wand = false` 关闭魔杖；`region mode true` 后：左键设 pos1、右键设 pos2 并应用区域，
  Shift+右键清除区域。

## 运行形态 / Environments

| 形态 | 说明 |
|---|---|
| `C only`（客户端装 mod，服务端不装） | 客户端本地对已加载区块计算并渲染 |
| `S+C` | 客户端本地计算并渲染（本版未启用服务端权威下发，见“已知限制”） |
| `S only`（客户端原版） | 服务端计算并发送聊天汇总 + 粒子 |

## 构建 / Build

多版本工程（replaymod-preprocessor 风格的子工程，`src/main` 共享 + `src/versions/<v>` 版本差异）。

```bash
./gradlew buildAndGather        # 构建全部 4 个版本并汇总到 build/libs
./gradlew :1.21.11:build        # 只构建某个版本
./gradlew :26.3:build           # 26.x 需要 JDK 25（Gradle toolchain 自动下载）
./gradlew :1.21.11:test         # 某版本的核心模型单测
./gradlew :1.21.11:runClient    # 开发客户端（运行目录为根目录 run/）
```

Windows PowerShell 使用 `.\gradlew.bat`。产物：`build/libs/rcv-mc<version>-<modversion>.jar`。

## 已知限制 / Known limitations

- 服务端权威的图下发（协议 v1 分块传输 `rcv:graph`）尚未实现：`S+C` 下由客户端本地计算。
- **26.3** 尚未实现 HUD 图例（新 HUD 使用 `GuiGraphicsExtractor` 抽取式 API）；渲染与命令正常。
- `compat` 复刻的红石线 / 中继器 / 比较器判定需与逐版本真实行为做游戏内对照（规划附录 D3）。
- PP `all` 档、NC `all` 档可能产生大量边，受节点 / 边上限保护。

## 配置 / Config

- `config/rcv-client.json`：颜色、备用调色板、线宽、默认深度、显示类型、HUD、自动清理、魔杖物品、PP/NC 模式。
- `config/rcv-server.json`：启用、权限、最大深度 / 节点 / 边、NC/PP 模式、轨道范围。
- GUI 依赖软依赖（`recommends`）：**Cloth Config** 与 **ModMenu**；缺失时 `/rcv config` 会给出提示。

## 致谢 / Credits

思路与架构参考以下项目（**参考实现均尽量重写**）：

| 项目 | 用途 | 许可 |
|---|---|---|
| SubTick / SubTick-fork | 多版本 preprocess、phase/queue、渲染组织 | LGPL-3.0 |
| microtimingreplay / fork | NC/PP 捕获、网络、UI、选区操作与可视化参考 | MIT |
| lucidity2.0 | Stonecutter、ModMenu、渲染管理 | 未声明 |
| ryansrenderingkit | 线框 / 文本（可选） | MIT |
| guardian | 逐版本反编译源码，核对判定（**不进产物**） | MC EULA |
| cloth-config | 客户端配置 UI | LGPL-3.0 |
| fabric-api / fabric-mod-template / ModMenu | 平台与工程 | Apache-2.0 / LGPL-3.0 / MIT |

本项目的红石判定均基于 Mojang 官方映射（**Mojmap**）下的原版行为复刻。
