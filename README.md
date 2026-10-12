# MTR-Ultra — Minecraft Transit Railway 大存档性能优化附属

> 独立 mixin 附属 mod（**不是** MTR/YMTR 的分支），挂钩 MTR 3.2.2 / YMTR 3.x（MC 1.20.1，Fabric+Forge）。
> 目标：让百车多线的大型存档流畅运行。定位激进优化——允许改变渲染/模拟细节行为。

## 架构

```
mtr-ultra/
├── build.gradle            # Architectury Loom 多项目(common/fabric/forge)
├── common/src/main/java/mtrultra/
│   ├── MtrUltraMod.java        # 主入口
│   ├── MtrUltraConfig.java     # 配置(每项优化独立开关, JSON 持久化)
│   ├── mixin/
│   │   ├── TrainServerMixin.java       # U4 模拟 LOD
│   │   ├── TrainMixin.java             # U1 scanDoors 短路
│   │   ├── TrainClientMixin.java       # U2 车厢级渲染剔除
│   │   ├── RenderTrainsMixin.java      # U3 轨道去重零分配
│   │   └── RailwayDataMixin.java       # U5 排班节流
│   └── compat/AnteCompat.java         # ANTE 检测与协商
├── fabric/src/main/resources/fabric.mod.json
└── docs/ultra-optimization-design.md    # 瓶颈分析与设计(自 yomis-mtr 移植)
```

## 优化项（对应实测瓶颈，全部有独立开关）

| # | 优化 | 挂钩点 | 收益(30列8车存档) |
|---|---|---|---|
| U1 | scanDoors 开门窗口短路 | Train.calculateCar | 每 tick 省 ~15 万次 getBlockState |
| U2 | 车厢级渲染剔除 | TrainClient.simulateCar | 远处列车 0 顶点提交 |
| U3 | 轨道去重表零分配 | RenderTrains.render | 每帧省数千 HashMap/UUID 分配 |
| U4 | 服务端模拟 LOD | TrainServer.simulateTrain | 远端列车开销 1/2~1/4 |
| U5 | 排班重建节流(20tick) | RailwayData/TrainServer | 近处列车排班开销 1/20 |

## 许可

MIT
