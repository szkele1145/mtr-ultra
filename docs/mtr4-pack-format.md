# MTR 4.0 标准车包格式实证（ERIDU-DG1000A 解包分析）

> 样本：用户提供"新艾利都地铁 ERIDU-DG1000A.zip"（11.6MB）。
> 这是 MTR Next（4.x 特性移植 YMTR 3.x）的核心格式情报。

## 车包整体结构

```
pack.mcmeta / pack.png              (标准资源包外壳)
assets/mtr/mtr_custom_resources.json   [4.0 车格式主入口]
assets/mtr/definition_*.json            [模型位置定义 ×15]
assets/mtr/properties_*.json            [连接面渲染属性 ×14]
assets/mtr/*.bbmodel                    [BlockBench 模型]
assets/mtr/*.png                        [贴图]
assets/mtr/sounds/s700_dg1000a/         [BVE 声音包: ogg + csv 调速曲线 + sound.cfg]
assets/mtrsteamloco/rails/rail.json     [ANTE 格式轨道样式(3.x 生态并存!)]
assets/mtrsteamloco/rails/*.obj+mtl     [轨道模型]
```

**关键发现：这个车包是 4.0 车格式 + 3.x(ANTE) 轨道样式的混合包**——
作者社区已经在双生态并存，与 MTR Next 的"双格式并存"定位天然契合。

## mtr_custom_resources.json 顶层

```json
{ "vehicles": [...], "signs": [], "rails": [], "objects": [], "lifts": [] }
```

## vehicles 段（编组的物理基础）

每辆车独立定义（本样本 3 节 = 头/中/尾）：

| 字段 | 值（M_F 头车样本） | 语义 |
|---|---|---|
| id / name | ERIDU-DG1000A(M_F) | M_F=Motor_Front, M_T=Middle, M_B=Back |
| color | D67C00 | 编组图标底色 |
| transportMode | TRAIN | 交通模式 |
| length / width | 12.0 / 3.0 | 几何尺寸（编组长度计算） |
| bogie1Position / bogie2Position | -4.0 / 4.0 | 两转向架位置 |
| couplingPadding1 / 2 | 0.0 / 0.0 | 连接面缩进（重联紧贴的几何补偿） |
| **hasGangway1 / 2** | false / **true** | 1/2 端是否有贯通道（风挡） |
| **hasBarrier1 / 2** | false / **true** | 1/2 端是否有端墙挡板 |
| models[] | 5 个 | 车体/内饰/LCD/驾驶面板多模型层 |
| ├ modelResource | mtr:eridudanguei.bbmodel | BlockBench 模型 |
| ├ textureResource | mtr:cheche1xa.png | 贴图 |
| ├ modelPropertiesResource | mtr:properties_1fa88c30.json | 渲染属性 |
| └ positionDefinitionsResource | mtr:definition_7183dc9.json | 乘客站位等 |
| flipTextureV | true | BlockBench 贴图 V 翻转惯例 |
| bogie1Models / bogie2Models | [] | 独立转向架模型（可空=车体自带） |
| bveSoundBaseResource | s700_dg1000a | BVE 声音集 |
| legacy* 字段 ×6 | — | 3.x 兼容字段（riderOffset/speedSound 等） |

**编组语义（用户描述的"头连头/重联"）**：
- M_F：仅 2 端可连（gangway2+barrier2）→ 编组首节，1 端是车头面
- M_T：双端可连 → 中间节
- M_B：仅 1 端可连 → 编组尾节
- **头连头 = 把 M_B 放 M_F 前面（1 端接 2 端）**；重联 = 同 id 车相邻；
  VehicleSelectorScreen 的 canAddCar 用这些连接面 + FAMILY_TAG 校验合法编组。

## properties_*.json（连接面渲染属性）

```json
{
  "parts": [],
  "modelYOffset": 1.0,
  "gangwayInnerSide/Top/BottomResource": "...",   // 贯通道内面贴图
  "gangwayOuter...Resource": "",                  // 外面贴图
  "gangwayWidth/Height/YOffset/ZOffset": ...,    // 贯通道几何
  "barrier...": ...                               // 端墙挡板同构字段
}
```
→ **4.x 的风挡/端墙是程序化几何**（宽高+偏移+四向贴图），
与 3.x TrainProperties 单一 hasGangwayConnection 布尔的粗糙实现完全不同。

## definition_*.json（positionDefinitions）

```json
{ "positionDefinitions": [
    { "name": "55e01484", "positions": [{"x":0,"y":0,"z":0}], "positionsFlipped": [] }
] }
```
→ 乘客站位/门位锚点（Flipped 变体支持镜像模型）。

## 声音包（BVE 格式）

`assets/mtr/sounds/s700_dg1000a/`：motor0-3.ogg（多档牵引音）+ flange/run/rub/
door 音 + `powerfreq.csv`/`powervol.csv`/`brakefreq.csv`/`brakevol.csv`
（速度→音高/音量映射曲线）+ sound.cfg。

## 对 MTR Next 移植设计的直接影响

1. **4.0 车包解析器**需要完整镜像 vehicles 段 schema（上面全部字段）+
   properties/definition 两级引用解析；
2. **bbmodel 渲染**：3.x 的 ANTE(sowcer) 已有 obj/bbmodel 管线可借力，
   或走 4.x 的 BlockBench 解析（模型 JSON 顶点直接渲）；
3. **编组存储**：VehicleCar(id, length, width, bogie×2, coupling×2,
   gangway/barrier 面向) + Siding 附加列表；
4. **legacy* 字段**：4.0 格式自带 3.x 回退字段——双版本并存的官方设计意图，
   MTR Next 的"仅接管 4.0 格式"判断可用 `mtr_custom_resources.json` 的
   vehicles 段存在性作为特征；
5. **声音**：BVE 调速曲线 csv 是 4.x 专属（3.x 用 speedSoundBase）——
   MTR Next 需要一个 BVE 播放器桥接（或退化为 legacy 字段）。
