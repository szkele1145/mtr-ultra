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
 * 安全边界：signalBlocks.resetOccupied()（每 tick 防撞）不受影响——
 * @Redirect 的 target 是 Map.clear 签名且 slice 限定在 clear 与
 * resetOccupied 之间。若同方法存在多个 Map.clear 调用，Redirect 要求
 * 唯一匹配会直接失败（mixin 配置 required=true 构建期报错）——
 * 保险起见用 ordinal 需先验证；此处先用宽匹配+运行时字段判定：
 * Redirect handler 内检查被清对象是否为 schedulesForPlatform。
 * <p>
 * 注意：TrainServer 端的排班重建段节流在 TrainServerMixin 中按
 * ScheduleRebuildFlag 实现（同 tick 全列车一致跳过，避免部分重建
 * 混入上一份数据造成重复排班）。
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
