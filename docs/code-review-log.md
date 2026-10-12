# MTR-Ultra 代码审查记录（A~L 审查点）

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

## 历史审查修复回顾

- **0.2.2**：LOD `near < mid` 钳制（手编 JSON 可使分级永假）+ `period >= 1`（取模崩溃）
- **0.2.3**：`ULTRA_INTERVAL` ThreadLocal 提前返回残留（上一列车 4 倍步长污染）+ 排班计数关闭期间重置
- **ANTE continue.2**：滚转排序缓存在 messagepack/packet 反序列化路径不失效（返回上一条轨道数据）

## 审查方法论

1. ThreadLocal 共享态：**所有**提前 return 路径必须考虑重置；
2. mixin `remap=false` + targets 字符串：MC 类描述符必须按平台（intermediary/官方名）适配；
3. 缓存失效：任何绕过 setter 的字段直接赋值路径都要同步失效缓存；
4. 注解参数表：能用 javap 核对的绝不凭记忆写。
