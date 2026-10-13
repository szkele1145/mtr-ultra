package mtrultra;

import java.util.concurrent.atomic.AtomicLong;

/**
 * [0.2.0] 性能仪表：各优化的实时命中计数器。
 * <p>
 * 由各 mixin handler 用 {@link #add} 累加（多线程安全：客户端渲染线程与服务端
 * tick 线程都可能命中），{@code /mtrultra stats} 命令读取快照。
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

	/** 上次 stats 读取的快照（用于计算速率） */
	private static long lastReadTime = 0;
	private static long lastDoorScan, lastCarsCulled, lastSimTicks, lastScheduleWrites;

	private MtrUltraStats() {
	}

	public static void add(AtomicLong counter) {
		counter.incrementAndGet();
	}

	/** 生成一行人类可读的累计+速率报告，并重置速率基准 */
	public static synchronized String report() {
		final long now = System.currentTimeMillis();
		final long d = doorScanSkipped.get();
		final long c = carsCulled.get();
		final long s = simTicksSkipped.get();
		final long w = scheduleWritesSkipped.get();

		final double dt = lastReadTime == 0 ? 0 : (now - lastReadTime) / 1000.0;
		final String rates = dt > 0
				? String.format(" | rates/s: scan=%.0f car=%.1f tick=%.1f sched=%.1f",
				(d - lastDoorScan) / dt, (c - lastCarsCulled) / dt, (s - lastSimTicks) / dt, (w - lastScheduleWrites) / dt)
				: "";

		lastReadTime = now;
		lastDoorScan = d;
		lastCarsCulled = c;
		lastSimTicks = s;
		lastScheduleWrites = w;

		return String.format("MTR-Turbo stats: scanDoorsShorted=%d (~%d block reads saved) | carsCulled=%d | simTicksSkipped=%d | scheduleWritesSkipped=%d%s",
				d, d * 600, c, s, w, rates);
	}
}
