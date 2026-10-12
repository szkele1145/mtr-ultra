package mtrultra.mixin;

/**
 * [U5 配套] 排班重建节拍广播（RailwayData → TrainServer）。
 * <p>
 * RailwayDataMixin 在 simulateTrains HEAD 写入本 tick 是否重建排班；
 * TrainServerMixin 在排班重建段读取并按需跳过。
 * 服务端 tick 线程串行执行 simulateTrains → 全部 simulateTrain，
 * ThreadLocal 广播无并发问题。
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
