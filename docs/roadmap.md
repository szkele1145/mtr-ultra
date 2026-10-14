# MTR Turbo 路线图

## 已发布（全部 Modrinth listed）

| 版本 | 内容 |
|---|---|
| 0.1.0 → 0.2.6 | 五大优化（U1-U5）、stats 命令、ModMenu/Forge 配置屏、LOD 钳制、ThreadLocal 残留修复、签名兼容实证、ANTE 共存（IMixinConfigPlugin） |
| 1.0.0 → 1.0.2 | MTR Turbo 改名（modid/命令/配置迁移）、Forge 端 ANTE 检测加固（mods 目录扫描） |
| 1.0.3 → 1.0.4 | 产物命名规范 `mtr-turbo-<ver>-<mc>-<loader>.jar`、多版本矩阵 1.18.2/1.19.2/1.19.4/1.20.1 × Fabric + 1.20.1 Forge |
| 1.0.5 | 周期性 stats 日志（`statsLogIntervalSeconds`，旧版本唯一可见入口） |

## 下一步（0.3.0 候选）

- 轨道渲染距离分批 + 视锥剔除（`renderRailStandardSafe` 内部，需字节码级调研）
- ~~Rail.render 回调闭包分配优化~~（**预研纠错**：发布 jar 常量池无 `Rail;render` 调用点——
  常规 TRAIN 走 renderRailStandardInternal 直接画（U3 已覆盖），rail.render 回调是
  CABLE_CAR 专用且通常个位数轨道，收益可忽略。不实施）
- ~~信号灯/轨道渲染 `getBrightness` 短期缓存~~（**预研纠错**：YMTR/官方发布 jar 的
  RenderTrains 常量池均无 getBrightness——此前结论误读自 yomis-mtr 源码树
  （≠YMTR 发布版）。轨道渲染是纯顶点提交无光照采样，此优化点不存在）
- YMTR 上游更新跟踪（签名变化预警：mixin required 启动即失败，而非静默错乱）
- 真实大存档 A/B 基准数据收集（用 1.0.5 的日志采集 + 1.1.3 的 dump 导出，等审核通过后社区协助）：
  - **可行性调研（2026-10-14）**：YMTR 发布 jar 无简单的程序化铺轨命令（轨道正常经网络包创建），
    但 `RailwayData.addRail(Player, TransportMode, BlockPos×2, Rail, boolean)` 是 **public**——
    一次性测试 mod 调它即可全自动预置含轨存档（Player 传 null 待试探；Rail 构造器
    `(long, TransportMode, BlockPos, BlockPos, float)` 已在字节码确认）；
  - **三条路径优先级**：①社区玩家现成存档 + dump（零成本，首选）②一次性 addRail 测试 mod
    全自动建存档（中等投入）③手工创造模式铺轨（最后手段）；
  - 拿到含轨存档后：开关对比 MSPT（`/spark tps` 或 forge/fabric 性能命令）+ stats/dump 计数即真实数字。

> 预研纪律记录：getBrightness 与 rail.render 两候选点均被发布 jar 字节码验证否决
> （第四次、第五次教训：源码树 ≠ 发布物，每个优化点实施前必须 javap 发布 jar）。

## 生态侦察（2026-10-14）

- **YMTR**：公开渠道最新仍 3.6.3（2026-09-19）；已支持的 3.6.4.2（用户渠道）为最前沿——无跟进压力。
- **MTR 官方**：主线已全面转向 4.x（4.1.0-beta.2，1.21.1/1.21.4，NeoForge+Fabric）；
  3.2.2 停在 1.20.1 及以下。4.x 是重写架构，mixin targets 与 3.x 完全不同。
- **决策**：不跟进 4.x——Turbo/ANTE 续更生态与 3.x 存量用户群（1.18.2~1.20.1）
  是真实存在的服务对象；待 4.x 生态成熟且 3.x 用户群衰减后再评估。
- **上游监控**：YMTR 若发 3.6.5+ 或 3.7，首要动作是对 11 个挂钩点重跑 javap
  签名核对（required=true 会启动期报警，不会静默错乱）。

## 设计原则

1. **每项优化独立开关**，默认全开，可逐项回退；
2. **行为保证**：手动/载客列车永不降频；骑乘列车永不剔除渲染；排班无空窗；
3. **失败要响**：target 失配在启动期报错（`required=true`），不静默；ANTE 冲突自动让位并日志明示；
4. mixin 全部 `remap=false` + `targets` 字符串——MC 类描述符按加载器平台适配；
5. **实测优先**：兼容性结论必须字节码核对或真实服务器冒烟，不做纸面推断（三方共存 6 轮测试的教训）。
