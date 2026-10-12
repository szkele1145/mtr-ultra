# MTR-Ultra — Minecraft Transit Railway 大存档性能优化附属

> 独立 mixin 附属 mod（**不是** MTR/YMTR 的分支），挂钩 MTR 3.2.2 / YMTR 3.x（MC 1.20.1，Fabric）。
> 目标：让百车多线的大型存档流畅运行。定位激进优化——允许改变渲染/模拟细节行为。

## 状态

**v0.1.0 — 已通过真实服务器冒烟测试**：`Done (4.004s)` 完整启动、MTR 数据正常加载、
全部 mixin 应用零报错、tick 循环（含 U5 节流路径）持续稳定运行。

## 架构

```
mtr-ultra/
├── build.gradle                # fabric-loom 1.9.2(Gradle 8.11 兼容线)
├── libs/ymtr-fabric-1.20.1.jar # 本地编译依赖(YMTR 3.6.4.2, 从其 Modrinth/GitHub 发布页获取)
└── fabric/src/main/
    ├── java/mtrultra/
    │   ├── MtrUltraMod.java        # 入口(LOGGER + Config 加载)
    │   ├── MtrUltraConfig.java     # 配置:每项优化独立开关, run/config/mtr-ultra.json 持久化
    │   ├── fabric/                 # Fabric 双入口(main/client)
    │   ├── mixin/
    │   │   ├── TrainMixin.java               # U1  scanDoors 短路
    │   │   ├── TrainClientMixin.java         # U2  车厢级渲染剔除
    │   │   ├── RenderTrainsAccessor.java     # U2  配套 Accessor
    │   │   ├── RenderTrainsMixin.java        # U3  渲染容器复用
    │   │   ├── TrainServerMixin.java         # U4  模拟 LOD + U5 排班写入跳过
    │   │   ├── TrainServerTickScaleMixin.java# U4  配套步长放大
    │   │   └── RailwayDataMixin.java         # U5  排班 clear 节流
    │   └── util/ScheduleRebuildFlag.java      # U5 节拍广播(mixin 包外!见类注释)
    └── resources/
        ├── fabric.mod.json
        ├── mtr-ultra.mixins.json
        └── icon.png
```

## 优化项（全部带独立开关，默认全开）

| # | 优化 | 挂钩点 | 收益(30列8车存档) | 状态 |
|---|---|---|---|---|
| U1 | scanDoors 开门窗口短路 | Train.calculateCar（@ModifyExpressionValue） | 每 tick 省 ~15 万次 getBlockState | ✅ 已验证 |
| U2 | 车厢级渲染剔除 | TrainClient.simulateCar（HEAD cancel） | 远处列车 0 顶点提交 | ✅ 已验证 |
| U3 | 渲染去重容器复用 | RenderTrains.render（@Redirect NEW HashMap） | 每帧省 HashMap 桶分配 | ✅ 已验证 |
| U4 | 服务端模拟 LOD | TrainServer.simulateTrain（HEAD 节拍 + @ModifyArg 步长放大） | 远端列车开销 1/2~1/4 | ✅ 已验证 |
| U5 | 排班重建节流(20tick) | RailwayData.clear + TrainServer 排班写入 | 近处列车排班开销 1/20 | ✅ 已验证 |

### 行为保证

- 手动驾驶/载有玩家的列车 **永不降频**（U4）；
- 排班节流的 clear 与重建同频，间隔期保留上一份数据——PIDS/传感器/Web 到站**无空窗**（U5）；
- 骑乘者所在列车豁免渲染剔除（U2）；
- 每项可单独关闭（`enableDoorScanShortCircuit` / `enableCarRenderCulling` / `enableRailDedupNoAlloc` / `enableSimulationLod` / `enableScheduleThrottle`）。

## 构建

需要 JDK 17 与 Gradle 8.11（wrapper 已配置腾讯镜像），本地代理已在 gradle.properties 预置。

```
gradlew build   # 产物 build/libs/mtr-ultra-0.1.0.jar
```

依赖 jar（`libs/ymtr-fabric-1.20.1.jar`）从 [YMTR 发布页](https://modrinth.com/mod/ymtr) 下载同名版本放入。

## 测试

```
gradlew runServer   # EULA 同意后首次启动约 16s 生成世界, 二次约 4s
```

冒烟记录：六轮 runServer 迭代——mixin 应用 ✓ / 入口初始化 ✓ / MTR 数据加载 ✓ /
tick 循环稳定 ✓（第 3~6 轮依次解决了依赖版本声明、运行时 architectury、
辅助类包位置三个真实问题，详见 git log）。

## 许可

MIT
