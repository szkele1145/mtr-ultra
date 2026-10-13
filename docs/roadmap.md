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
- 客户端 `Rail.getPosition` 曲率缓存（**预研完成**：87 条字节码/次，30 列车约 270 次/tick
  的三角函数链；方案=按 (rail, 弧长步进) 采样点缓存，WeakHashMap 外挂，轨道不可变则缓存永真）
- 信号灯/轨道渲染 `getBrightness` 短期缓存（**预研完成**：renderRailStandardInternal
  每轨每帧 newBlockPos+getBrightness×2；方案=BlockPos→packed light 的 1 tick TTL Map，
  光照更新延迟 1 tick 视觉不可察觉）
- YMTR 上游更新跟踪（签名变化预警：mixin required 启动即失败，而非静默错乱）
- 真实大存档 A/B 基准数据收集（用 1.0.5 的日志采集功能，等审核通过后社区协助）

## 设计原则

1. **每项优化独立开关**，默认全开，可逐项回退；
2. **行为保证**：手动/载客列车永不降频；骑乘列车永不剔除渲染；排班无空窗；
3. **失败要响**：target 失配在启动期报错（`required=true`），不静默；ANTE 冲突自动让位并日志明示；
4. mixin 全部 `remap=false` + `targets` 字符串——MC 类描述符按加载器平台适配；
5. **实测优先**：兼容性结论必须字节码核对或真实服务器冒烟，不做纸面推断（三方共存 6 轮测试的教训）。
