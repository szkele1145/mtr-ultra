package mtrultra.util;

/**
 * [U5 配套] 排程重建节拍广播（RailwayData → TrainServer）。
 * <p>
 * RailwayDataMixin 在 simulateTrains HEAD 写入本 tick 是否重建排程；
 * TrainServerMixin 在排程写入处读取并按需跳过。
 * 服务端 tick 线程串行执行 simulateTrains → 全部 simulateTrain，
 * ThreadLocal 广播无并发问题。
 * <p>
 * 注意：此类必须位于 mtrultra.util 而非 mtrultra.mixin 包——
 * mixin 包内的非 mixin 类被 mixin 系统禁止直接引用
 * （IllegalClassLoadError: cannot be referenced directly）。
 */
public final class ScheduleRebuildFlag {

	private static final ThreadLocal<Boolean> REBUILD = ThreadLocal.withInitial(() -> Boolean.TRUE);

	private ScheduleRebuildFlag() {
	}

	public static void set(boolean rebuild) {
		REBUILD.set(rebuild);
	}

	public static boolean isSet() {
		return REBUILD.get();
	}
}
