# MTR Turbo 多版本支持方案（1.18.2 / 1.19.2 / 1.19.4 / 1.20.1）

> 2026-10-13 评估，2026-10-21 扩充完整矩阵。签名核对基于真实 jar javap。

## 当前支持矩阵（1.1.6 起）

| 加载器 | 1.18.2 | 1.19.2 | 1.19.4 | 1.20.1 |
|---|---|---|---|---|
| Fabric | ✅ | ✅ | ✅ | ✅ |
| Forge | — | — | ✅ | ✅ |

Fabric 四版本 + Forge 两版本 = **6 个产物**。

## 依赖矩阵（`build.gradle` 的 `depMatrix`）

| MC 版本 | 编译目标 jar | 说明 |
|---|---|---|
| 1.20.1 | `libs/ymtr-fabric-1.20.1.jar` | YMTR 3.6.4.2（活跃维护） |
| 1.19.2 | `libs/ymtr-fabric-1.19.2.jar` | YMTR 3.6.2 |
| 1.19.4 | `libs/mtr-fabric-1.19.4.jar` | 官方 MTR 3.2.2-hotfix-1（YMTR 无此版本） |
| 1.18.2 | `libs/mtr-fabric-1.18.2.jar` | 官方 MTR 3.2.2-hotfix-1 |

Forge（`forge-project/build.gradle` 的 `depMatrix`）：

| MC 版本 | 编译目标 jar | Forge 版本 | 备注 |
|---|---|---|---|
| 1.20.1 | `libs/ymtr-forge-1.20.1.jar` | `1.20.1-47.2.0` | 运行时需 `architectury-forge 9.2.14`（YMTR 的 mods.toml 声明 mandatory） |
| 1.19.4 | `libs/mtr-forge-1.19.4.jar` | `1.19.4-45.4.5` | 官方 MTR 3.2.2-hotfix-1，无 YMTR |

## 兼容性判定（mixin 目标签名核对）

**逐个目标 jar 用 `javap -p` 核对**（2026-10-21，覆盖 6 个 jar）：

| 挂钩点 | 1.20.1 | 1.19.2 | 1.19.4 | 1.18.2 | 结论 |
|---|---|---|---|---|---|
| `Train.calculateCar(Level, Vec3[], int, int, CCB)` | ✅ | ✅ | ✅ | ✅ | 一致 |
| `Train.scanDoors(Level, DDDFFDI)Z` | ✅ | ✅ | ✅ | ✅ | 一致 |
| `Train.getRoutePosition(int, int)` | ✅ | ✅ | ✅ | ✅ | 一致 |
| `TrainServer.simulateTrain`（8 参） | ✅ | ✅ | ✅ | ✅ | 一致 |
| `TrainServer.lambda$simulateTrain$8(Map,long,long,TimeSegment)` | ✅ | ✅ | ✅ | ✅ | 一致 |
| `RailwayData.simulateTrains()` | ✅ | ✅ | ✅ | ✅ | 一致 |
| `RenderTrains.render(EntitySeat, float, PoseStack, MultiBufferSource)` | ✅ | ✅ | ✅ | ✅ | 一致 |
| `RenderTrains.maxTrainRenderDistance`（public static int） | ✅ | ✅ | ✅ | ✅ | 一致 |
| `TrainClient.simulateTrain(Level, float, SpeedCallback, Ann, Ann)` | ✅ | ✅ | ✅ | ✅ | 一致 |
| `TrainClient.simulateCar`（15 参） | ✅ | ✅ | ✅ | ✅ | 一致 |

**MTR 3.2.2-hotfix-1 与 YMTR 3.6.x 的包结构一致**（都是 `mtr.data.*` / `mtr.render.*`），
且 `mtr.data.Train` 的公开成员逐项一致：

| 成员 | 可见性 | 6 个 jar 是否一致 |
|---|---|---|
| `doorTarget` | protected | ✅ |
| `doorValue` | protected | ✅ |
| `isCurrentlyManual` | protected | ✅ |
| `path` | **public final** `List<PathData>` | ✅ |
| `isPlayerRiding(Player)` | **public final** boolean | ✅ |
| `RailwayData.schedulesForPlatform` | private final `Map<Long, List<ScheduleEntry>>` | ✅ |

## ⚠️ @Shadow 可见性铁律（2026-10-21 修复的真 bug）

Mixin **运行时**会校验 `@Shadow` 声明的可见性不得低于目标：

```
%s %s method %s in %s cannot reduce visibiliy of %s target method
```

（`MixinPreProcessorStandard` 字节码常量池；抛出 `InvalidMixinException` ⇒ 直接崩服/崩客户端。）

**注解处理器（AP）只在编译期给 `warning: Cannot find target for @Shadow ...`，
运行期才炸** —— 所以「构建通过」不代表「运行时安全」。

原代码把 `path` 声明为 `protected`（目标实为 `public final`）、把 `isPlayerRiding`
声明为 `protected abstract`（目标实为 `public final`）—— 属于**可见性降级**，
在 1.19.4 forge 这个**新平台**上是崩溃级风险。1.20.1 已发布版本同样带此隐患。

**修复**：`TrainServerMixin`（fabric + forge 两份）的 shadow 改为与目标逐字一致：

```java
@Shadow protected boolean isCurrentlyManual;        // 目标 protected
@Shadow public List<PathData> path;                 // 目标 public final
@Shadow public abstract boolean isPlayerRiding(Player player);  // 目标 public final
```

Mixin 运行时通过 `ClassInfo.findMethodInHierarchy(..., Traversal)` **沿继承链查找**，
所以 shadow 父类（`Train`）的成员是合法的 —— AP 的 "Cannot find target" 警告是
静态分析限制，**不是**运行时错误。

## 各版本差异处理

| 差异点 | 1.18.2 | 1.19.2 | 1.19.4 | 1.20.1 |
|---|---|---|---|---|
| ModMenu 配置屏（`GuiGraphics` 1.20 才引入） | ❌ 排除 | ❌ 排除 | ❌ 排除 | ✅ |
| `/mtrturbo stats` 命令（fabric-api 0.77.0+1.18.2 无 command v2 包） | ❌ 排除 | ✅ | ✅ | ✅ |
| Forge 配置屏 | — | — | ❌ 排除（改用反射打开，1.19.x 提示不可用） | ✅ |
| `fabric.mod.json` 的 `modmenu` 入口点 | 移除 | 移除 | 移除 | 保留 |
| `fabric.mod.json` 的 `suggests` | 移除 | 移除 | 移除 | 保留 |

**Forge 1.19.4 的配置屏处理**（`forge-project`）：

- `MtrUltraForgeConfigScreen` 独立成文件，仅 1.20.x 参与编译
  （build.gradle 的 `hasConfigScreen` 开关排除）。
- `MtrUltraForgeClientCommands` 改用**反射**打开配置屏
  （`Class.forName("mtrultra.forge.MtrUltraForgeConfigScreen")`），
  编译期不依赖该类；1.19.x 运行时给出明确提示，用户仍可手编 `config/mtr-turbo.json`。

## 明确不做的

- **1.20.4 / 1.21.x**：MTR 4.0.5 起是**大重构**（`mtr.*` → `org.mtr.*`，
  `Train`/`TrainClient` → `Vehicle`/`VehicleCar`），turbo 的全部 mixin 目标类
  **不存在**。支持它等于重写 turbo，暂不做（用户 2026-10-21 明确「不需要兼容 mtr4.0」）。
- 1.20.4 的 MTR jar（`mtr-fabric-1.20.4.jar` / `mtr-forge-1.20.4.jar`）
  已下载后**删除**，避免误用。

## 构建方式

```powershell
# Fabric
& 'F:\vibe coding工程\build.ps1' mtr-turbo build -PbuildVersion=1.19.4

# Forge
& 'F:\vibe coding工程\build.ps1' mtr-turbo\forge-project build -PbuildVersion=1.19.4
```

（`build.ps1` 挂 `Z:` 绕开中文路径；Gradle 8.11；`GRADLE_USER_HOME=C:\gradle-home`。）

产物名：
- `mtr-turbo-<ver>-<mc>-fabric.jar`（`build\libs\`）
- `mtr-turbo-<ver>-<mc>-forge.jar`（`forge-project\build\libs\`）
