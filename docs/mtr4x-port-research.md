# MTR Next — 4.x 特性移植 YMTR 3.x 的调研笔记（主线程发现）

> 2026-10-14。项目：MTR Next（用户定名）——把 MTR 4.x 的轨道预览 +
> 自定义编组带回 YMTR 3.x 的独立 mixin 附属（非 fork）。
> 约束：自定义编组仅接管 4.0 车包格式；3.2 车包不接管、原样由 YMTR 处理。

## 发现一：4.x 编组体系全貌（VehicleSelectorScreen 链）

用户确认："编组"=4.x 车厂面板的可视化车辆编成编辑器——车头/车厢类型可选，
支持自定义连接模式（如头连头、重联）。

反编译实证的核心数据链：

```
VehicleResource (4.x 车包资源, org.mtr.resource, schema 化 ReaderBase)
   │  getId/getName/getTransportMode/getLength/getWidth/getCapacity
   │  getBogie1Position/getBogie2Position/getCouplingPadding1/2
   │  hasGangway1/2 hasBarrier1/2 (连接面定义!)
   │  VehicleModel 池(车体模型 + bogie1/2 转向架模型分离)
   │  getCachedVehicleResource(carNumber, totalCars) — 按编组位缓存
   ▼ toVehicleCar()
VehicleCar (编组单元, org.mtr.core.data, VehicleCarSchema)
   │  vehicleId/length/width/capacity/bogie1Position/bogie2Position
   │  hasOneBogie (单车转向架 — 链接"头连头"的几何基础)
   │  getTotalLength(b1, b2)/getCouplingPadding1/2(bool) — 连接面语义
   ▼ selectedVehicleCars (ObjectArrayList<VehicleCar>)
Siding (车厂侧线持有编组, org.mtr.core.data)
   ▼ VehicleSelectorScreen (Elementa UI 编组编辑器)
      │  selectedTags(tag 过滤) / availableVehicles / selectedVehicles
      │  canAddCar(编组约束检查) / drawVehicleIcon(编组位图标)
      │  FAMILY_TAG — 家族标签(同家族车可重联?)
```

**关键洞察**：
1. 4.x 的"编组"就是 Siding 里的 `ObjectArrayList<VehicleCar>`——每辆车带
   couplingPadding/gangway/barrier 连接面语义，**头连头=两车的 barrier/coupling
   面相接**，重联=同型车 family tag 相邻。这与 3.x Siding 的
   `trainCars: List<TrainType>`（仅车型 ID 串）完全不同粒度。
2. VehicleResource 的 `getCachedVehicleResource(carNumber, totalCars)` 表明
   **模型渲染按"编组位置"缓存**（第 i 节/共 n 节）——编组长度变化时缓存键变。
3. 4.x UI 用 Elementa（gg.essential.elementa）框架——移植时不能搬 UI，
   只能搬数据模型+用 3.x 原版控件重写界面。

## 发现二：YMTR 3.x 侧的对应结构（移植目标）

```
CustomResources (mtr.client, 车包加载器)
   │  reload(ResourceManager) → Gson JsonObject 手工解析
   │  readResource(world, path, Consumer<JsonObject>)
   ▼
TrainProperties (单车型属性, 无编组语义)
   │  baseTrainType/name/color/riderOffset/bogiePosition
   │  isJacobsBogie/hasGangwayConnection (粗糙的连接标志)
   │  renderer/sound
   ▼
Train.trainCars: List<TrainType> (编成在 Depot GUI 手工配, 无连接面语义)
Siding (mtr.data) — 3.x 无 VehicleCar 概念
```

**移植边界（用户约束的落点）**：
- 4.0 车包（VehicleResource schema 格式）→ MTR Next **接管解析**，
  转成 TrainProperties+扩展编组数据
- 3.2 车包（CustomResources Gson 格式）→ **不接管**，YMTR 原样处理，
  MTR Next 对其零侵入（识别格式特征后跳过）

## 待子代理报告补充

- 轨道预览的完整帧循环（手持→第一节点→getRail→绘制的每步与网络频率）
- 4.x 车包文件的具体形态（schema JSON 长什么样、放 assets 哪个路径）
- YMTR 侧 mixin 挂钩点候选的最终签名清单

## 初步架构构想（待细化）

```
MTR Next/
├── fabric/ (1.20.1, loom 复用 mtr-turbo 构建链)
│   ├── mtrnext/VehicleResourceLoader   [4.0 车包解析器(镜像 VehicleResource 字段)]
│   ├── mtrnext/FormationStore          [Siding→编组数据扩展(附加存储)]
│   ├── mtrnext/screen/FormationScreen  [原版控件编组编辑器(替代 Elementa)]
│   ├── mtrnext/mixin/RailPreviewMixin  [预览渲染: 挂 RenderTrains.render]
│   └── mtrnext/mixin/SidingMixin      [编组数据挂载点]
├── forge-project/ (后续)
└── docs/
```

风险初判：
1. 4.x 车包解析在 3.x 侧需要完整镜像 VehicleResource/VehicleModel 的模型
   管线（obj 渲染）——工作量大头，可能要借用 ANTE 的 sowcer 渲染器
   （它已有 obj 模型管线）
2. 编组数据的持久化：3.x Siding 的 messagepack 序列化不含编组字段——
   附加存储要么扩 messagepack（侵入）要么另存 sidecar 文件
3. 预览渲染挂 RenderTrains.render 的 HEAD（与 U3 的 @Redirect 不同方法，
   无冲突——MTR Turbo 共存性要专项验证）
