package mtrultra.mixin;

import mtrultra.MtrUltraConfig;
import mtrultra.util.ScheduleRebuildFlag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * [U5] 排班重建节流。
 * <p>
 * 原版 RailwayData.simulateTrains 每 tick 执行：
 * schedulesForPlatform.clear() → 全列车遍历重建排班 → 同 tick 内消费。
 * 客户端同步本就每 60 tick 拉取一次，1 秒粒度对可见性零影响。
 * <p>
 * 节流（clear 与重建同频，间隔期保留上一份数据，PIDS/传感器无空窗）：
 * - HEAD 注入：维护节拍计数，写入 {@link ScheduleRebuildFlag}（ThreadLocal
 *   广播给 TrainServer 端的伴生 mixin）；
 * - @Redirect 精确替换 schedulesForPlatform.clear()：非重建 tick 不清空。
 * <p>
 * <b>与 ANTE 共存（require=0 优雅降级）</b>：ANTE 的 RailwayDataMixin
 * 整体覆写了 simulateTrains（含自己的轨道遍历优化），三方实测中本 mixin
 * 对该方法的注入会被拒（"merged by"冲突）。因此本类全部注入点
 * require = 0：应用失败仅记录警告、不崩溃——U1-U4 照常工作，
 * U5 的排班节流在 ANTE 环境自动让位（ScheduleRebuildFlag 保持默认
 * TRUE = 每 tick 重建，与原版行为一致）。
 * <p>
 * 安全边界：signalBlocks.resetOccupied()（每 tick 防撞）不受影响——
 * @Redirect 的 target 是 Map.clear 签名，handler 内检查被清对象是否为
 * schedulesForPlatform。
 */
@Mixin(targets = "mtr.data.RailwayData", remap = false)
public abstract class RailwayDataMixin {

	// ★ [1.1.8 真机修复] 目标是 **private final** Map<Long, List<ScheduleEntry>>
	// schedulesForPlatform（6 个目标 jar 逐一 javap 核实）。
	// 本字段在 RailwayData **自身**（不是父类）⇒ @Shadow 能定位；
	// 但目标带 final 标志，Mixin 校验规则要求 shadow 也要声明 @Final
	// （否则报 "@Shadow target {}::{} is final but shadow is not decorated with @Final"）。
	// 注意：Java 侧不能写 `final`（字段初始化会报错），final 语义由 @Final 注解承担。
	@Shadow
	@Final
	private Map<Long, List<Object>> schedulesForPlatform;

	@Unique
	private long mtrultra$scheduleTickCounter = 0;

	@Inject(method = "simulateTrains", at = @At("HEAD"), require = 0)
	private void mtrultra$scheduleThrottle(CallbackInfo ci) {
		boolean rebuild;
		if (!MtrUltraConfig.enableScheduleThrottle) {
			rebuild = true;
			mtrultra$scheduleTickCounter = 0; // [0.2.3] 关闭期间重置，重开后从第 1 tick 即重建
		} else {
			mtrultra$scheduleTickCounter++;
			final int period = Math.max(1, MtrUltraConfig.scheduleRebuildTicks);
			// [1.1.5] 修复：原判据 `counter % period == 1` 在 period=1 时恒为 false
			// （counter % 1 恒为 0），导致排班表被永久冻结——clear 与 add 同时被跳过。
			// scheduleRebuildTicks=1 是可达配置（MtrUltraConfig 只钳 Math.max(1, ...)），
			// 且用户的自然语义就是「每 tick 重建 = 关闭节流」。
			// 改用 (counter-1) % period == 0：counter 从 1 起算，首 tick 即重建，
			// period=1 时恒为 true（每 tick 重建），period=N 时每 N tick 重建一次。
			rebuild = (mtrultra$scheduleTickCounter - 1) % period == 0;
		}
		ScheduleRebuildFlag.set(rebuild);
	}

	/**
	 * 替换 simulateTrains 内的 Map.clear 调用：仅当
	 * (a) 节流开启且非重建 tick，且 (b) 被清对象是 schedulesForPlatform 时跳过；
	 * 其余情况（含其他 Map 的 clear，若存在）原样执行。
	 */
	@Redirect(
			method = "simulateTrains",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V"),
			require = 0
	)
	private void mtrultra$throttledClear(Map<Object, Object> map) {
		if (MtrUltraConfig.enableScheduleThrottle
				&& !ScheduleRebuildFlag.isSet()
				&& map == (Map<Object, Object>) (Object) schedulesForPlatform) {
			return; // 非重建 tick：保留上一份排班数据
		}
		map.clear();
	}
}
