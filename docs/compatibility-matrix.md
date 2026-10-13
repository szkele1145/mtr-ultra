# MTR Turbo 目标签名兼容矩阵（字节码实证）

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

## 与 ANTE 的共存（0.2.5 实测修正）

初版矩阵推断"互不干扰"——**三方实测推翻了它**：

- ANTE 的 `RailwayDataMixin` **整体覆写**了 `simulateTrains()`（含其轨道遍历优化），
  MTR Turbo 对同一方法的 U5 注入在 PREINJECT Applicator 阶段被硬拒绝
  （`merged by` 异常，`require=0` 也无法压制——两次实测确认）；
- **修复**：`IMixinConfigPlugin`（Fabric: `FabricLoader.isModLoaded("mtrsteamloco")`；
  Forge: `ModList.get().isLoaded`）检测 ANTE 在场时跳过 U5 的 RailwayDataMixin，
  启动日志输出明确提示；
- **语义**：U5 让位后排班回到原版每 tick 行为（`ScheduleRebuildFlag` 默认 TRUE），
  U1-U4 全部照常（不同目标类，与 ANTE 的覆写无交集）；
- **验证**：YMTR 3.6.4.2 + ANTE 1.1.1-continue.2 + MTR Turbo 0.2.5 三方同装，
  服务器 `Done (13.654s)` 后持续稳定。

### 为什么只有 U5 冲突（mixin 机制分析）

| MTR Turbo mixin | ANTE 对同类的操作 | 共存性 |
|---|---|---|
| RailwayDataMixin（U5） | ANTE **@Overwrite 整个 simulateTrains** | ❌ 覆写挡住一切后续注入 → 跳过 |
| TrainClientMixin（U2） | ANTE 仅 @Inject HEAD/TAIL（局部钩子） | ✅ 多注入可共存（v4 实测通过） |
| RenderTrainsMixin（U3） | ANTE 仅 @Inject（render/renderRailStandard 各自钩子） | ✅ 不同方法/不同注入点共存 |
| TrainServerMixin / TrainMixin | ANTE 未 mixin 这两个类 | ✅ 无交集 |

mixin 机制结论：**@Inject 局部钩子彼此天然共存；@Overwrite（或隐式整方法替换）
是排他的**——两个 mixin 覆写同一方法必炸，覆写者对所有后来者关门。
写跨 mod mixin 时，要么只用 @Inject，要么准备好 IMixinConfigPlugin 让位协议。

## 意义

1. MTR Turbo 对 **MTR 官方 3.2.2 与 YMTR 全线（3.6.3~3.6.4.2）双支持**，
   不是设计推断而是逐签名实证；
2. YMTR 是官方 MTR 的功能 fork，其 `mtr.data` 核心模拟/渲染类签名长期稳定，
   预计小版本演进继续兼容（若破坏，mixin `required=true` 启动期即报，不会静默）；
3. 验证材料：`F:\tools\mtr-322-forge.jar`（官方）、对比命令为 javap 逐项核对。

## 与发布页的对应

Modrinth 描述中的 "verified signature-compatible down to the latest public
release (3.6.3)" 依据即本矩阵；官方 3.2.2 的验证结论后续将并入页面说明。
