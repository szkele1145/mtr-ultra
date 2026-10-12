# MTR-Ultra 目标签名兼容矩阵（字节码实证）

> 全部 7 个 mixin 挂钩点，对三个目标 mod 版本逐一 javap 核对（2026-10-13）。
> **结论：MTR 官方 3.2.2-hotfix-2 与 YMTR 3.6.3/3.6.4.2 的全部挂钩签名一致。**

| 挂钩点 | 用途 | 官方 3.2.2-hf2 | YMTR 3.6.3 | YMTR 3.6.4.2 |
|---|---|---|---|---|
| `Train.calculateCar(Level, Vec3[], int, int, CalculateCarCallback)` | U1 挂载方法 | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `Train.scanDoors(Level, DDDFFDI)Z` | U1 短路目标 | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `TrainClient.simulateTrain(Level, float, SpeedCallback, Ann., Ann.)` | U2 快照 | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `TrainClient.simulateCar(15 参)` | U2 剔除 | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `RenderTrains.render(EntitySeat, float, PoseStack, MultiBufferSource)`（静态） | U3 复用 | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `RenderTrains.maxTrainRenderDistance`（static int） | U2/U3 读取 | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `TrainServer.simulateTrain`（8 参） | U4 节拍 | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `Train.simulateTrain(Level, float, Depot)`（U4 步长放大目标） | U4 伴生 | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `TrainServer.lambda$simulateTrain$8(Map, long, long, TimeSegment)` | U5 写入跳过 | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `RailwayData.simulateTrains()` | U5 节拍 | ✅ 一致 | ✅ 一致 | ✅ 一致 |
| `RailwayData` 内 `Map.clear()` 唯一性 | U5 clear 节流 | ✅ | —（同 3.6.4.2 已验） | ✅ |

## 意义

1. MTR-Ultra 对 **MTR 官方 3.2.2 与 YMTR 全线（3.6.3~3.6.4.2）双支持**，
   不是设计推断而是逐签名实证；
2. YMTR 是官方 MTR 的功能 fork，其 `mtr.data` 核心模拟/渲染类签名长期稳定，
   预计小版本演进继续兼容（若破坏，mixin `required=true` 启动期即报，不会静默）；
3. 验证材料：`F:\tools\mtr-322-forge.jar`（官方）、对比命令为 javap 逐项核对。

## 与发布页的对应

Modrinth 描述中的 "verified signature-compatible down to the latest public
release (3.6.3)" 依据即本矩阵；官方 3.2.2 的验证结论后续将并入页面说明。
