# MTR-Ultra 性能基准与 stats 解读指南

> 配合 `/mtrultra stats` 命令（0.2.0+）使用。所有数字来自源码级分析与
> 30 列 8 车厢存档的典型形态（YMTR 3.6.4.2，MC 1.20.1）。

## 各优化的理论收益

### U1 Door-scan short-circuit（`scanDoors` 开门窗口短路）

**原版行为**：`Train.calculateCar` 对每节车厢每 tick 无条件调用 `scanDoors` 两次
（左右各一），每次扫描 `3 × 6 × (spacing+1)` ≈ 306~612 格方块（spacing 8~16 时）。
列车**绝大多数时间不在站台**，这些读取全部无效（结果只在 `doorValue > 0` 时才被消费）。

**量级**：30 列 × 8 车 × 2 次 × ~300 格 = **每 tick ~15 万次 getBlockState**
（服务端+客户端各一遍，同一列车的模拟两侧都会跑 calculateCar）。
 cruising 状态下列车几乎 100% 时间在开门窗口外——U1 全额短路。

**stats 指标**：`scanDoorsShorted`（每次 ≈600 格方块读取）。
**解读**：`rates/s: scan=150000` 表示当前每秒短路 15 万次扫描 ≈ 9000 万格/秒的读取被省去。

### U2 Car render culling（车厢级渲染剔除）

**原版行为**：`TrainClient.simulateCar` 对每节车厢无条件提交渲染
（renderCar + 半透明批 + 车钩连接件）。YMTR 有全局 `maxTrainRenderDistance`
（信号灯/PIDS 用它），但列车本体不查距离。

**量级**：远处列车（几十格外）的每车厢模型顶点提交是帧率大头；
剔除后 draw call 与顶点提交按可视列车数收缩。视距拉满时收益最大。

**stats 指标**：`carsCulled`（剔除的车厢·帧次数）。
**解读**：`car=240.0/s` ≈ 每帧 4 节车厢被剔除（60 FPS 时）。

### U3 Render container reuse（渲染容器复用）

**原版行为**：`RenderTrains.render` 每帧 new 一个轨道去重 HashMap +
每条轨 new 一个 UUID。大存档数千条轨 × 60 FPS 是稳定的 GC 压力。

**量级**：HashMap 桶数组（数千条目 → 64~128 桶 × Node 分配）+
数千 UUID/帧。U3 后该路径零分配（U3 的 stats 不单独计数——它不是"命中"型优化）。

### U4 Simulation LOD（服务端模拟 LOD）

**原版行为**：服务端每 tick 全速模拟全部列车（哪怕 500 格外无玩家看着）。

**量级**：中距离（128~256 格）列车降为 1/2 频率、远处（>256 格）1/4。
30 列车若有 20 列在远处，服务端列车模拟开销 **约降 40%**。
列车速度上限 ~1.2 格/tick → 4 tick 间隔位移 ≤5 格 < 最小轨段，防撞不漏检。

**stats 指标**：`simTicksSkipped`（跳过的列车·tick）。
**解读**：`tick=750.0/s` ≈ 每 game tick（20/s）跳过 37 个列车·tick ≈ 9 列车按 1/4 频率跑。

### U5 Schedule throttle（排班重建节流）

**原版行为**：`RailwayData.simulateTrains` 每 tick clear + 全列车重建排班表。
但客户端同步本就每 60 tick 拉一次，1 秒粒度（20 tick）对 PIDS/传感器/WebView 完全够。

**量级**：近处列车的排班重建开销 **1/20**（远处的由 U4 顺带覆盖）。

**stats 指标**：`scheduleWritesSkipped`（跳过的排班写入）。

## 快速基准流程

1. 备份存档，服务端装 MTR-Ultra，OP 加入自己；
2. `/mtrultra stats` 第一次跑（建立速率基准）；
3. 站在枢纽站观察 60 秒（列车密度最高处）；
4. 再次 `/mtrultra stats` 读 `rates/s`；
5. 关闭全部开关（改 config）重启，同位置对比 `/spark tps` 或
   `/forge metrics`（Forge）/`/fabric performance`（Fabric）的服务端 tick 曲线。

## 兼容性矩阵

| 目标 | 兼容性 |
|---|---|
| YMTR 3.6.4.2（Fabric/Forge） | ✅ 全部五大优化（实测冒烟通过） |
| YMTR 3.6.3（公开最新，Fabric/Forge） | ✅ 全部 7 个 mixin 目标签名经 javap 逐项核对一致（2026-10-13 验证） |
| MTR 3.2.2 官方（含 hotfix-2） | ✅ **全部挂钩签名经 javap 实证一致**（详见 compatibility-matrix.md） |
| ANTE（续更版） | ✅ ANTE 的轨道渲染接管时 U3 的 render 注入仍在原版方法体内生效——互不干扰 |
| JCM/YUM 等附属 | ✅ 无共享类（它们挂的是 mtr.block/mtr.client，与 U1-U5 目标无交集） |

> 若未来 YMTR 更新改变目标方法签名：相关 mixin 会在启动期大声失败
> （`required=true`），而不是静默错乱——看启动日志定位是哪一项。
> 上游监控：3.6.4.2 为当前已知最新（用户提供）；Modrinth 公开渠道最新为
> 3.6.3，两者签名一致，预计小版本演进签名稳定。
