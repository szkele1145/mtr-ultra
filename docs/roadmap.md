# MTR-Ultra 路线图

## 0.1.0 ✅（已发布，Fabric，Modrinth beta 审核中）

U1-U5 五大优化 + 真实服务器冒烟测试通过 + GitHub/Modrinth 双发布。

## 0.2.0（进行中）

| 项 | 状态 |
|---|---|
| Forge 1.20.1 版（forge-project，双端 mixin target 适配） | 🔄 子代理实现中 |
| 性能仪表：`/mtrultra stats` 命令——输出各优化实时命中数（剔除车厢计数、跳过 tick 计数、短路扫描计数、排班跳过计数） | 📋 |
| ModMenu 配置 GUI（Fabric）/ Forge 配置屏 | 📋 |
| 大存档实测基准报告（30+ 列存档：帧率/MSPT 前后对比） | 📋 |

## 0.3.0+

- 轨道渲染距离分批 + 视锥剔除（`renderRailStandardSafe` 内部，需字节码级调研）
- 客户端 `Rail.getPosition` 曲率缓存（移植 ANTE 续更版思路）
- 信号灯 `getBrightness` 按 chunk 缓存 1-2 tick
- YMTR 上游更新跟踪（签名变化预警：mixin required 启动即失败，而非静默错乱）

## 设计原则

1. **每项优化独立开关**，默认全开，可逐项回退；
2. **行为保证**：手动/载客列车永不降频；骑乘列车永不剔除渲染；排班无空窗；
3. **失败要响**：target 失配在启动期报错（`required=true`），不静默；
4. mixin 全部 `remap=false` + `targets` 字符串——MC 类描述符按加载器平台适配。
