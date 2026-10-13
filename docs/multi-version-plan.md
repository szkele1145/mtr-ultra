# MTR Turbo 多版本支持方案（1.19.2 / 1.19.4 / 1.20.1）

> 2026-10-13 评估。签名核对基于真实 jar javap。

## 兼容性判定（mixin 目标签名核对）

| 挂钩点 | 1.20.1（YMTR 3.6.x / MTR 3.2.2） | 1.19.2（YMTR 3.6.2） | 1.19.4（MTR 3.2.2-hf1） |
|---|---|---|---|
| `Train.calculateCar(class_1937, class_243[], int, int, CCB)` | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `Train.scanDoors(class_1937, DDDFFDI)Z` | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `TrainServer.simulateTrain`（8 参，全部 intermediary 类型） | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `RenderTrains.render(EntitySeat, float, class_4587, class_4597)` | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `lambda$simulateTrain$8(Map, long, long, TimeSegment)` | ✅ 一致 | ✅ 一致 | 待验（大概率一致） |

**结论：Turbo 的全部 mixin targets 在三个 MC 版本上签名完全一致**
（mtr.data.* 是 mod 自身类，不随 MC 版本变；intermediary 类名在
1.19.2/1.19.4/1.20.1 的 Fabric 生态中稳定——1.19.4 与 1.20.1 的差异
主要在 net.minecraft 类的新增，不在这些被引用的类上）。

**待办**：`lambda$simulateTrain$8` 与 `RailwayData.simulateTrains` 在
1.19.4 的最终核对（预计一致）。

## 版本矩阵与目标用户

| MC 版本 | MTR 官方 | YMTR | Turbo 支持意义 |
|---|---|---|---|
| 1.20.1 | 3.2.2 | 3.6.x（活跃） | ✅ 已支持（Fabric+Forge） |
| 1.19.2 | 3.2.2 | 3.6.2 | YMTR 用户群 |
| 1.19.4 | 3.2.2-hf1 | 无 | MTR 官方用户群（用户点名要求 Fabric） |

## 构建方案（每个目标版本一套 loom 配置）

现有 fabric 构建只绑 1.20.1。多版本走 **Gradle 属性切换**（与
mtr-ante 的 `-PbuildVersion` 同思路，但更简单——Turbo 无 manifold
预处理）：

1. `gradle.properties` 增加 `minecraft_version` 属性（默认 1.20.1）；
2. `build.gradle` 读属性决定 `minecraft`/`mappings`/`fabric-api`
   依赖版本（1.19.2: 0.77.0+1.19.2 / 1.19.4: 0.87.2+1.19.4）；
3. `libs/` 增加对应版本的 YMTR/MTR jar 作为 modImplementation；
   - 1.19.2 → `libs/ymtr-fabric-1.19.2.jar`（YMTR 3.6.2）
   - 1.19.4 → `libs/mtr-fabric-1.19.4.jar`（官方 3.2.2-hf1，YMTR 无此版本）
4. 产物命名带版本后缀（mtr-turbo-1.0.3+1.19.2.jar 等）。

**风险点**：
- 1.19.4 无 YMTR → U4 LOD 的"最近玩家"遍历用的是 world.players()
  （1.19.2/1.19.4/1.20.1 三版本同名同签名）无风险；
- `Class.forName` ANTE 探测/`mods 目录扫描`双端方案与 MC 版本无关；
- fabric-api 的 CommandRegistrationCallback 在 1.19.2/1.19.4 的
  v2 包名相同（2.x 系列）；
- ModMenu：1.19.2 用 4.2.0-beta.2、1.19.4 用 6.3.1（modCompileOnly
  需按版本切）。

## 实施顺序

1. 先做 1.19.4 Fabric（用户点名）：改 build.gradle 属性化 + 换
   fabric-api/ModMenu 版本 + 用官方 MTR 3.2.2-hf1 编译 + runServer 冒烟；
2. 再做 1.19.2 Fabric：同参数换 YMTR 3.6.2；
3. Modrinth 版本条目分别挂 game_versions。
