package mtrultra.mixin;

import mtrultra.MtrUltraConfig;
import mtrultra.MtrUltraStats;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * [U6 / 0.3.0] Rail.getPosition 曲率缓存。
 * <p>
 * Train.getRoutePosition 每次对车厢调用 Rail.getPosition(progress)——
 * 87 条字节码的二次曲线三角函数链。每 tick 全部列车约 270 次（30 列 8 车）
 * 重算几乎相同的采样点（匀速时相邻 tick 差 <0.06 格）。
 * <p>
 * 缓存策略：每条 Rail 按 0.25 格弧长步长预采样位置数组，查询线性插值。
 * Rail 几何一经创建不可变（MTR 改轨道会重建 Rail 实例）→ 缓存永真。
 * 数组惰性构建（getLength/0.25 ≈ 4~64 个 Vec3）。
 * <p>
 * 精度：0.25 格插值偏差 <0.125 格，轨道纹理最小 1 格，视觉不可辨。
 * <p>
 * 挂点：Train.getRoutePosition（private）内对 Rail.getPosition 的唯一
 * 调用（偏移 72）。@Redirect 替换为缓存查询；缓存表为 ThreadLocal 的
 * WeakHashMap（服务端 tick 与客户端渲染各自单线程持有独立表，
 * 弱键随 Rail 回收）。
 * <p>
 * Redirect handler 首参需要 Rail 类型——用 targets 字符串无法 import Rail，
 * 首参写 Object 并在内部以 mtr.data.Rail 造型（编译期 loom 的 modImplementation
 * 提供了 Rail 类，直接 import 亦可；此处直接 import 保证类型安全）。
 */
@Mixin(targets = "mtr.data.Train", remap = false)
public abstract class RailPositionCacheMixin {

	/** 步长 0.25 格 */
	private static final double STEP = 0.25D;

	/** 缓存表：线程本地弱键 Map（Rail 几何不可变 → 无失效逻辑） */
	private static final ThreadLocal<Map<Object, Vec3[]>> MTRTURBO$POS_CACHE =
			ThreadLocal.withInitial(() -> Collections.synchronizedMap(new WeakHashMap<>()));

	@Redirect(
			method = "getRoutePosition",
			at = @At(value = "INVOKE", target = "Lmtr/data/Rail;getPosition(D)Lnet/minecraft/class_243;")
	)
	private Vec3 mtrturbo$cachedGetPosition(Object rail, double progress) {
		if (!MtrUltraConfig.enableRailPositionCache) {
			return ((mtr.data.Rail) rail).getPosition(progress);
		}
		final Map<Object, Vec3[]> cache = MTRTURBO$POS_CACHE.get();
		Vec3[] samples = cache.get(rail);
		if (samples == null) {
			samples = mtrturbo$buildSamples((mtr.data.Rail) rail);
			cache.put(rail, samples);
		}
		// progress 越界钳制（原方法 progress>=0 由调用方 Math.max 保证，上界防御）
		final double len = samples.length * STEP;
		final double p = progress < 0 ? 0 : Math.min(progress, len - STEP);
		final int idx = (int) (p / STEP);
		final double frac = (p - idx * STEP) / STEP;
		final Vec3 a = samples[idx];
		final Vec3 b = samples[Math.min(idx + 1, samples.length - 1)];
		return new Vec3(
				a.x + (b.x - a.x) * frac,
				a.y + (b.y - a.y) * frac,
				a.z + (b.z - a.z) * frac
		);
	}

	private static Vec3[] mtrturbo$buildSamples(mtr.data.Rail rail) {
		final double length = rail.getLength();
		final int n = Math.max(2, (int) Math.ceil(length / STEP) + 1);
		final Vec3[] out = new Vec3[n];
		for (int i = 0; i < n; i++) {
			final double d = Math.min(i * STEP, length);
			out[i] = rail.getPosition(d);
		}
		return out;
	}
}
