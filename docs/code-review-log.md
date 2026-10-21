# MTR Turbo 代码审查记录（A~L 审查点）

> 0.2.3 发布后对全部 mixin 与 UI 代码做的系统性审查。结论：1 处死代码已修，
> 1 处风格记录（不改），10 处确认安全。

| # | 审查点 | 结论 |
|---|---|---|
| A | RenderTrainsMixin 复用表生命周期（ThreadLocal 渲染线程唯一 + HEAD 清理） | ✅ 安全 |
| B | 复用表不被 render 之外引用（原局部变量语义保持） | ✅ 无泄漏 |
| C | TrainClientMixin @Inject Object 参数（字节码协变匹配） | ✅ 合法 |
| D | TrainClientMixin `mtrultra$snapshotFrame` 死代码 | 🔧 已移除（0.2.4 提交） |
| E | `ticksElapsed=0` 静态渲染路径的剔除行为 | ✅ 一致 |
| F | 半透明批（trainTranslucentRenders）随 simulateCar cancel 一并跳过 | ✅ 已验证 |
| G | MtrUltraStats.report() 的 synchronized 与 AtomicLong 并发 | ✅ 安全 |
| H | 配置屏 init() 重复构建组件 | ✅ MC 管理生命周期 |
| I | ULTRA_INTERVAL ThreadLocal 初始化边界 | ✅ 安全 |
| J | Forge TrainMixin @Redirect 首参为 mixin 类型 | 📝 风格非惯用，功能正确（冒烟已证），不改 |
| K | Button.builder(Component, OnPress) 参数顺序 | ✅ javap 核对正确 |
| L | drawCenteredString(Font, Component, x, y, color) | ✅ javap 核对正确 |

## 1.1.x 审查（U6 曲率缓存新代码）

| # | 审查点 | 结论 |
|---|---|---|
| M | U6 缓存表 ThreadLocal 弱键——客户端渲染/服务端 tick 独立表，轨道移除后可回收 | ✅ 安全 |
| N | 采样数组构建的越界（progress 上界钳制 len-STEP；n≥2 防除零） | ✅ 已钳制 |
| O | 插值端点 idx+1 越界（Math.min 封顶） | ✅ 安全 |
| P | 缓存永真前提——Rail 几何不可变（MTR 编辑轨道重建实例，弱键旧表自动失联） | ✅ 成立（无失效逻辑需要的前提） |
| Q | 精度上界——0.25 格步长线性插值偏差 <0.125 格 vs 轨道纹理最小 1 格 | ✅ 视觉不可辨（数学推导在 mixin 注释） |
| R | RailPositionCacheMixin @Redirect 与 U4/U5 的 TrainServer mixin 无同方法冲突（getRoutePosition 是 Train 的方法，其余挂 simulateTrain/calculateCar） | ✅ 无交集 |
| S | 双端描述符——Fabric `class_243` / Forge `net/minecraft/world/phys/Vec3`（1.18.2~1.20.1 逐字核对偏移 72） | ✅ javap 实证 |
| T | stats 接入的 add() 在 Redirect handler 尾部（未启用缓存时直接 return 原始调用，不计数——语义正确：只统计缓存命中） | ✅ 语义正确 |

## 1.1.6 审查（U~X：相机时序 + @Shadow 可见性）

| # | 审查点 | 结论 |
|---|---|---|
| U | **相机快照陈旧问题**（U2 剔除依据的 `cameraPos` 是否读到上一帧位置） | ✅ **不成立**。javap 实证：`GameRenderer.mainCamera` 是 `private final` 字段（永不重建），`Camera.position` 每帧由 `Camera.setup(...)` 刷新；字节码偏移显示 `Camera.setup()`（offset 449）严格早于 `LevelRenderer.renderLevel(camera.getPosition(), …)`（offset 524/528）。turbo 的快照在 `TrainClient.simulateTrain` HEAD 读取 ⇒ 拿到的是**当前帧**相机位置，无陈旧问题。**唯一冗余**是快照字段按 `TrainClient` 实例存储、却每列车重读一次（无害） |
| V | **`@Shadow` 可见性降级 ⇒ 运行时崩溃**（`TrainServerMixin` 的 `path` / `isPlayerRiding`） | 🔧 **已修（1.1.6）**。MTR 的 `mtr.data.Train` 中 `path` = `public final List<PathData>`、`isPlayerRiding(Player)` = `public final boolean`；原代码声明为 `protected` / `protected abstract` ⇒ **降级**。Mixin 运行时校验 `"%s %s method %s in %s cannot reduce visibiliy of %s target method"`（`MixinPreProcessorStandard` 字节码常量池）⇒ 抛 `InvalidMixinException` **直接崩客户端/崩服务端**。注解处理器只在编译期给 `warning: Cannot find target for @Shadow`（静态分析限制：成员在父类 `Train` 上），**运行期才炸** ⇒ 「构建通过」≠「运行时安全」。已改为与目标逐字一致（`public` / `public abstract`），可见性经 **6 个目标 jar** 逐一 javap 核对 |
| W | **1.19.4 Forge 目标签名兼容性**（新平台，此前从未构建） | ✅ 全部 10 个挂钩点签名与 1.20.1 完全一致：`Train.calculateCar` / `Train.scanDoors` / `Train.getRoutePosition` / `TrainServer.simulateTrain` / `TrainServer.lambda$simulateTrain$8(Map,long,long,TimeSegment)` / `RailwayData.simulateTrains()` / `RenderTrains.render(EntitySeat,float,PoseStack,MultiBufferSource)` / `RenderTrains.maxTrainRenderDistance`（`public static int`）/ `TrainClient.simulateTrain` / `TrainClient.simulateCar`（15 参）。`mtr.data.Train` 的 5 个成员（`doorTarget`/`doorValue`/`isCurrentlyManual`/`path`/`isPlayerRiding`）与 `RailwayData.schedulesForPlatform`（`private final Map<Long, List<ScheduleEntry>>`）在 6 个 jar 上逐项一致 |
| X | **`forge-project/gradle.properties` 版本号独立于主工程** | 🔧 **已修（1.1.6）**。`forge-project` 是**独立 Gradle 项目**（有独立 `gradle.properties`），此前主工程 `mod_version` 升到 1.1.6 时 forge 端仍是 1.1.5 ⇒ 产物名与 mod 版本不一致，Modrinth/GitHub 会挂错版本号。已同步为 1.1.6 |

### V 项的验证方法（可复用）

```powershell
# 1) 查目标 jar 中成员的真实可见性
javap -p <jar> mtr.data.Train | Select-String 'isPlayerRiding|path'
# 2) 查 mixin 运行时的可见性校验规则（常量池）
javap -p -c org/spongepowered/asm/mixin/transformer/MixinPreProcessorStandard.class \
  | Select-String 'cannot reduce visibiliy'
```

**结论**：`@Shadow` 声明的可见性**必须 ≥ 目标可见性**。AP 警告可忽略（父类成员），
但可见性降级是**运行期致命**。发布前必须逐 jar javap 核对。


## 双端一致性审计（2026-10-14，1.1.3 后）

| 维度 | 方法 | 结果 |
|---|---|---|
| Stats 类 | report() 逐字节比对 + 差异定位 | ✅ 分叉仅在 dumpJson 的加载器 API（预期）；Forge 曾缺 minecraft 字段已补（1.1.4 候选） |
| Config 类 | 10 字段逐一计数 + 钳制逻辑正则比对 + save() addProperty 计数 | ✅ 三项零漂移 |
| mixins.json | 条目集合 Compare-Object | ✅ mixins 5+client 3 双端一致 |
| 审计方法 | 哈希不同→定位差异范围→逐段确认，不做无脑同步 | ✅ 有意分叉保留，无意漂移归零 |

## 历史审查修复回顾

- **0.2.2**：LOD `near < mid` 钳制（手编 JSON 可使分级永假）+ `period >= 1`（取模崩溃）
- **0.2.3**：`ULTRA_INTERVAL` ThreadLocal 提前返回残留（上一列车 4 倍步长污染）+ 排班计数关闭期间重置
- **ANTE continue.2**：滚转排序缓存在 messagepack/packet 反序列化路径不失效（返回上一条轨道数据）
- **1.1.5**：排班节流判据 `% period == 1` 在 `period == 1` 时恒假 ⇒ **排班表永久冻结**
  （clear 与 add 同时被跳过）。`scheduleRebuildTicks=1` 是可达配置
  （`MtrUltraConfig` 只钳 `Math.max(1, ...)`），且用户的自然语义就是
  「每 tick 重建 = 关闭节流」。改用 `(counter - 1) % period == 0`：
  首 tick 即重建，`period=1` 恒 true，`period=N` 每 N tick 一次。
  双端同步（fabric / forge-project 的 `RailwayDataMixin.java` 同一行）。
- **1.1.5**：`org.gradle.java.home` 从 `Z:/jdk17` 改回 `C:/jdk17`
  （`C:\jdk17` 是指向 `F:\vibe coding工程\jdk17` 的 junction，永久有效且纯 ASCII）。
  `Z:` 由 `build.ps1` 临时 `subst`，脚本结束即删——标准构建流程下可用，
  但 IDE / CI / 独立 `gradlew` 会失效。同批清掉死配置
  `org.gradle.java.installations.paths=F:/tools/jdk17`（该目录不存在，
  且工程无任何 toolchain 配置引用它）。

## 审查方法论

1. ThreadLocal 共享态：**所有**提前 return 路径必须考虑重置；
2. mixin `remap=false` + targets 字符串：MC 类描述符必须按平台（intermediary/官方名）适配；
3. 缓存失效：任何绕过 setter 的字段直接赋值路径都要同步失效缓存；
4. 注解参数表：能用 javap 核对的绝不凭记忆写。
