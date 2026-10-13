# Modrinth 审核等待须知（两个项目共用）

> 来源：[Modrinth Help Center — Project Review Times](https://support.modrinth.com/en/articles/8793355-project-review-times)（本周更新）

## 关键结论

1. **当前审核延迟严重**：官方正在培训新内容审核员，审核时间"upwards of two weeks, sometimes over a month"（两周起，可能超一个月）——我们两个项目提交不足 24 小时，处于完全正常的等待期。

2. **修改/上传新版本不会重排队**：审核中的项目即使继续上传新版本（我们已传了 1.0.5→1.1.1 共 15 个新版本），**队列位置不变**。但**删除旧版本**可能把项目踢到队尾——我们已经做过几次删除旧版本（删 mtr-ultra 旧文件名版本），万幸官方说明是"删除后上传新版本可能重排"而我们的删除发生在重发之前，位置应该还在。

3. **不要催**：官方明说询问进度会拖慢所有人。项目"Under review"（即 processing）状态**一定会被审核**，不会丢失。

4. **代码安全扫描**：Modrinth 有内部恶意代码检测器，被标记的项目会走技术审核员人工复审——mixin 类 mod（大量字节码注入）有一定概率触发这条路径，若审核时间特别长可能是在走这个流程。

## 行动

- **不催、不改队列行为**（不再删版本）
- 继续正常迭代——新版本照常发布，位置不受影响
- 审核期间 GitHub 渠道已可正常分发（MTR-ANTE 的 4 jar 全矩阵已挂 Release）
- MTR Turbo 的 GitHub Release 页面建议也挂上最新 jar 作为审核期备选渠道
