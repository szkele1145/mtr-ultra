package mtrultra.mixin;

import mtrultra.MtrUltraConfig;
import mtrultra.util.ScheduleRebuildFlag;
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
 * [U5] 排班重建节流（Forge 版）。
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
 * 安全边界：Redirect handler 内检查被清对象是否为 schedulesForPlatform，
 * 其余 Map 的 clear 原样执行。
 * <p>
 * Forge 版差异：无（target 全不含 MC 类，javap 已验证 simulateTrains 与
 * schedulesForPlatform 字段存在）。
 */
@Mixin(targets = "mtr.data.RailwayData", remap = false)
public abstract class RailwayDataMixin {

	@Shadow
	private Map<Long, List<Object>> schedulesForPlatform;

	@Unique
	private long mtrultra$scheduleTickCounter = 0;

	@Inject(method = "simulateTrains", at = @At("HEAD"))
	private void mtrultra$scheduleThrottle(CallbackInfo ci) {
		boolean rebuild;
		if (!MtrUltraConfig.enableScheduleThrottle) {
			rebuild = true;
		} else {
			mtrultra$scheduleTickCounter++;
			final int period = Math.max(1, MtrUltraConfig.scheduleRebuildTicks);
			rebuild = mtrultra$scheduleTickCounter % period == 1;
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
			at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V")
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
