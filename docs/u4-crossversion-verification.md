# U4 LOD 跨版本方法链接验证（1.18.2 深挖记录）

> 2026-10-14 保活轮次。起因：审查 U4 依赖的 `world.players()` 与 `Player` 实体
> 在最老版本 1.18.2 的链接正确性（此前只验了 mixin targets 的 mod 侧签名）。

## 验证链（三层证据）

1. **产物层**：`mtr-turbo-1.1.1-1.18.2-fabric.jar` 的 `TrainServerMixin.class`
   常量池含 `method_18456`（`players()` 的 intermediary 名）——loom 的 remap
   已把 mojmap 源码调用正确转为 intermediary 输出 ✓
2. **依赖层**：loom 缓存的 1.18.2 minecraft-merged jar（mojmap 名）中
   `net/minecraft/world/level/Level.class` 存在且含 `players` 方法 ✓
   （该 jar 是编译期用 mojmap 映射的 merged 产物，运行时生产环境对应
   intermediary 的 class_1937/method_18456——Fabric 官方 yarn 保证
   1.18.2~1.20.1 的 `players()` intermediary 名稳定）
3. **运行层（最强证据）**：1.18.2 冒烟 `Done (10.548s)` 持续稳定运行——
   U4 在真实 1.18.2 服务端完整跑过启动期与 tick 期，若 `players()` 链接
   断裂会在首次模拟 tick 抛 NoSuchMethodError/VerifyError 当场崩溃。

## 结论

`world.players()` 在 1.18.2 产物正确链接；mixin 的 mod 侧 targets（mtr.data.*）
四版本签名一致 + MC 侧调用的 intermediary 名跨版本稳定 + 三层证据闭环。
U4 的 1.18.2 支持与 1.19.2/1.19.4/1.20.1 等同可靠。

## 方法论补充

- loom 的 minecraft-merged 缓存是 **mojmap 名**（因 build.gradle 用
  `officialMojangMappings`），不要拿它当 intermediary jar 查类；
- 验证 mixin 产物里的 MC 方法调用，直接搜产物 class 的常量池找
  `method_XXXXX`（remap 后的 intermediary 名）即可，比反查 MC 本体快；
- 中文用户名路径会让 .NET `ZipFile::OpenRead` 间歇性失败——用
  `Get-ChildItem` 传 PSObject 的 FullName 而非硬编码字符串。
