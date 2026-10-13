package mtrultra;

import java.util.concurrent.atomic.AtomicLong;

/**
 * [0.2.0] 性能仪表：各优化的实时命中计数器。
 * <p>
 * 由各 mixin handler 用 {@link #add} 累加（多线程安全：客户端渲染线程与服务端
 * tick 线程都可能命中），{@code /mtrturbo stats} 命令读取快照。
 * <p>
 * [1.0.5] 周期日志输出：{@code statsLogIntervalSeconds} 配置 > 0 时，每 N 秒
 * 向日志（INFO 级）输出一行累计报告——1.18.2/1.19.x 版本没有 stats 命令与
 * 配置屏，这是旧版本用户查看优化命中的唯一入口；1.20.x 用户也可用它做
 * 无人值守采集。默认关闭（0）。
 * <p>
 * 计数器按优化编号命名，语义：
 * - doorScanSkipped: U1 开门窗口外被短路的 scanDoors 次数（每省一次≈600 格方块读取）
 * - carsCulled: U2 因超距被剔除渲染提交的车厢次数
 * - simTicksSkipped: U4 LOD 跳过的列车-tick 数
 * - scheduleWritesSkipped: U5 非重建 tick 跳过的排班写入次数
 */
public final class MtrUltraStats {

	public static final AtomicLong doorScanSkipped = new AtomicLong();
	public static final AtomicLong carsCulled = new AtomicLong();
	public static final AtomicLong simTicksSkipped = new AtomicLong();
	public static final AtomicLong scheduleWritesSkipped = new AtomicLong();
	// [1.1.1] U6 曲率缓存命中（每次插值查询计 1；对比原始 87 条字节码重算）
	public static final AtomicLong railPositionCached = new AtomicLong();

	/** 上次 stats 读取的快照（用于计算速率） */
	private static long lastReadTime = 0;
	private static long lastDoorScan, lastCarsCulled, lastSimTicks, lastScheduleWrites, lastRailPosition;

	/** [1.0.5] 周期日志的下次输出时刻（毫秒），0=功能未启用 */
	private static volatile long nextLogAt = 0;

	private MtrUltraStats() {
	}

	public static void add(AtomicLong counter) {
		counter.incrementAndGet();
		// [1.0.5] 热路径顺带检查周期日志（读一个 volatile + 一次时间调用，
		// 未到期时开销可忽略；到期时输出并顺延——add 调用频率远高于日志周期，
		// 不需要独立调度线程）
		final long at = nextLogAt;
		if (at != 0) {
			final long now = System.currentTimeMillis();
			if (now >= at) {
				nextLogAt = now + MtrUltraConfig.statsLogIntervalSeconds * 1000L;
				MtrUltraMod.LOGGER.info(report());
			}
		}
	}

	/** [1.0.5] 配置加载后调用：interval<=0 关闭（保持 0），否则立即开始首个周期 */
	public static void initPeriodicLog() {
		if (MtrUltraConfig.statsLogIntervalSeconds > 0) {
			nextLogAt = System.currentTimeMillis();
			MtrUltraMod.LOGGER.info("MTR-Turbo periodic stats logging every {}s", MtrUltraConfig.statsLogIntervalSeconds);
		} else {
			nextLogAt = 0;
		}
	}

	/** 生成一行人类可读的累计+速率报告，并重置速率基准 */
	public static synchronized String report() {
		final long now = System.currentTimeMillis();
		final long d = doorScanSkipped.get();
		final long c = carsCulled.get();
		final long s = simTicksSkipped.get();
		final long w = scheduleWritesSkipped.get();
		final long rp = railPositionCached.get();

		final double dt = lastReadTime == 0 ? 0 : (now - lastReadTime) / 1000.0;
		final String rates = dt > 0
				? String.format(" | rates/s: scan=%.0f car=%.1f tick=%.1f sched=%.1f railpos=%.0f",
				(d - lastDoorScan) / dt, (c - lastCarsCulled) / dt, (s - lastSimTicks) / dt, (w - lastScheduleWrites) / dt, (rp - lastRailPosition) / dt)
				: "";

		lastReadTime = now;
		lastDoorScan = d;
		lastCarsCulled = c;
		lastSimTicks = s;
		lastScheduleWrites = w;
		lastRailPosition = rp;

		return String.format("MTR-Turbo stats: scanDoorsShorted=%d (~%d block reads saved) | carsCulled=%d | simTicksSkipped=%d | scheduleWritesSkipped=%d | railPositionCached=%d (~%d trig chains saved)%s",
				d, d * 600, c, s, w, rp, rp, rates);
	}

	/**
	 * [1.1.3] 把当前计数器快照写成 config/mtrturbo-stats.json（/mtrturbo dump）。
	 * Forge 版：FMLPaths.CONFIGDIR + loader 标记 forge。
	 */
	public static int dumpJson() {
		try {
			final com.google.gson.JsonObject root = new com.google.gson.JsonObject();
			root.addProperty("timestamp", System.currentTimeMillis());
			root.addProperty("mod", "mtr-turbo");
			root.addProperty("loader", "forge");
			root.addProperty("minecraft", guessMcVersion());
			final com.google.gson.JsonObject counters = new com.google.gson.JsonObject();
			counters.addProperty("doorScanSkipped", doorScanSkipped.get());
			counters.addProperty("carsCulled", carsCulled.get());
			counters.addProperty("simTicksSkipped", simTicksSkipped.get());
			counters.addProperty("scheduleWritesSkipped", scheduleWritesSkipped.get());
			counters.addProperty("railPositionCached", railPositionCached.get());
			root.add("counters", counters);
			final java.nio.file.Path out = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("mtrturbo-stats.json");
			java.nio.file.Files.writeString(out, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
			return 5;
		} catch (final Exception e) {
			MtrUltraMod.LOGGER.error("Failed to write stats dump", e);
			return 0;
		}
	}

	/** MC 版本探测（Forge 稳定链： FMLLoader.versionInfo() 不可用时退 unknown） */
	private static String guessMcVersion() {
		try {
			final Object version = net.minecraftforge.fml.loading.FMLLoader.class
					.getMethod("versionInfo").invoke(null);
			return String.valueOf(version);
		} catch (final Throwable t) {
			return "unknown";
		}
	}
}
