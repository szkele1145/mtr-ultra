# MTR Ultra（暂定名）—— 基于 YMTR 的激进优化模组 · 架构设计 v1

> 基座：[yomi-china/Yomis-Minecraft-Transit-Railway](https://github.com/yomi-china/Yomis-Minecraft-Transit-Railway)（MTR 3.2.2 完整 fork，MC 1.20.1，Fabric+Forge 双端，MIT→ARR 源码可用）
> 定位：大型存档（百车多线）性能优先，允许改变渲染/模拟行为细节（与 ANTE 续更版的"保守"相对）
> 日期：2026-10-12

---

## 一、瓶颈实测清单（基于 YMTR `030061e` 源码静态分析）

### S1. `scanDoors` 方块扫描风暴（最高优先级）
- 位置：`Train.calculateCar` → `scanDoors`（Train.java:865-866），**每车每 tick 左右各一次、无条件执行**
- 量级：8 节车厢 spacing=16 → 每侧 3×6×17=306 格，双侧 612 格/tick/车；30 列车 = **~15 万次 getBlockState/tick**（服务端+客户端各一遍）
- 关键：其结果只用于 `openDoors(...)`（开门检测）与 `hasPlatform`。**绝大多数 tick 列车不在站台附近**
- 激进方案：
  - **站台空间索引**：ClientData/服务端 RailwayData 维护 `platformId → [blockPos 范围]`（平台与 PSD 方块注册时已存在）；列车按 railProgress 所在 rail 是否落入某平台 AABB 决定是否执行 `scanDoors`，否则直接返回 false
  - 兜底开关：索引失效时退回原始扫描（正确性优先）
  - 预期收益：**90%+ 的 scanDoors 调用被消除**

### S2. 渲染端无逐车距离剔除
- 位置：`RenderTrains.render` → `ClientData.TRAINS.forEach(train.simulateTrain)`，`TrainClient.simulateCar` **对每节车厢无条件 renderCar**（TrainClient.java:103）
- 现状：只有 `renderDetails`（>32 格减细节，JonModelTrainRenderer.java:110）与全局 `maxTrainRenderDistance`（仅用于信号/PIDS 等，不用于列车本体！）
- 激进方案：
  - 每列车按车头位置与相机距离做**列车级剔除**（超 `trainRenderDistance` 整列不渲染，但模拟继续）；近列再按**车厢级剔除**（单节车厢超距跳过 renderCar/renderConnection）
  - 音频同理：`trainSound.playAllCars` 按距离静音（sound 系统本身有衰减，但调度开销仍在）
  - 预期收益：大型存档渲染 draw call 与顶点提交数量级下降

### S3. `ClientData.RAILS` 全量遍历 + 每轨 lambda/对象分配
- 位置：`RenderTrains.render` 每帧对 `ClientData.RAILS.forEach((startPos, railMap) -> ...)`（RenderTrains.java:332 附近），每条轨：`PathData.getRailProduct`（new UUID 对象）、`renderedRailMap`（每帧 new HashMap）、`rail.render(lambda)`、`newBlockPos`、`getBrightness×2`
- 激进方案：
  - **轨道渲染空间分桶**：按 chunk（或 4×4 chunk）预建 `rail 列表` 快照，结构变更才重建（复用 ANTE 续更版 BakedRail 思路）；渲染时只遍历视锥/距离内桶
  - `renderedRailMap` 快照化/复用；`getRailProduct` 改为 long 打包（UUID→8B）
  - 信号灯/轨道光照：把 `getBrightness` 结果按 chunk 缓存 1-2 tick（光照更新频率远低于帧率）

### S4. 服务端每 tick 全量 `sidings.forEach(simulateTrain)`
- 位置：`RailwayData.simulateTrains`（RailwayData.java:321）→ `Siding.simulateTrain`（Siding.java:438）
- 现状：所有侧线所有列车每 tick 全速模拟（哪怕 1000 格外无玩家）
- 激进方案：
  - **模拟 LOD**：距最近玩家 > N（默认 512 格）的列车降频模拟（每 4 tick 一次，位移按 4× 积分）；> M（默认 2048 格）只做排班/进位推进，不做信号检测与 scanDoors
  - 无人区块的 `signalBlocks.resetOccupied` 逐轨重置改位图增量
  - 预期收益：服务端 MSPT 大存档下降 50%+（列车模拟通常占服务端 tick 大头）

### S5. 排班计算每 tick 全量展开
- 位置：`TrainServer.simulateTrain`（TrainServer.java:258）中 `timeSegments` 遍历 + `schedulesForPlatform` 重建，**每列车每 tick 都做**
- 激进方案：排班表按 `TICKS_TO_SEND_RAIL_PROGRESS`（已有节流）粒度重建，铁路进度未跨段时复用上次结果

### S6. 客户端 `handlePositions`/`renderPlayerAndGetOffset` 的 Map 装箱
- 位置：`VehicleRidingClient`（percentagesX/Z 等 `Map<UUID, Float>` 每帧装箱）
- 激进方案： riding 玩家数通常个位数，收益小，暂不动（记录在案）

## 二、实现策略

- **不 fork 发布**，直接在 YMTR 源码上维护分支 `ultra`：模组即"优化版 YMTR"，随上游（Yomi）更新 rebase
- 每个优化点独立提交 + 独立配置开关（`Config` 新增 ultra 分类，默认全开，可逐项关闭回退 vanilla 行为）
- 与 ANTE 续更版的关系：ANTE 侧已有 `AnteRailCompat.java`（YMTR 内置兼容层），ultra 版继续兼容 ANTE 续更版（轨道渲染层级协商）

## 三、里程碑

| 阶段 | 内容 | 验收 |
|---|---|---|
| U1 | S1 站台索引 + scanDoors 短路 | 30 列 8 车存档服务端 MSPT 可测下降；开关关闭行为与原版一致 |
| U2 | S2 列车/车厢级渲染剔除 | 渲染距离外 0 draw call；驾驶视角无异常 |
| U3 | S3 轨道分桶渲染 | 轨道渲染遍历量 ≤ 视锥内轨道数 |
| U4 | S4 模拟 LOD | 远端列车推进不倒退、进站无跳变 |
| U5 | S5 排班节流 + 收尾 | 大存档实测报告（帧率/MSPT 前后对比） |
