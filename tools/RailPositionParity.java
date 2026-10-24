import mtr.data.Rail;
import mtr.data.RailAngle;
import mtr.data.RailType;
import mtr.data.TransportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

/**
 * turbo U6「Rail.getPosition 曲率缓存」数值对拍器。
 *
 * <p><b>要验证什么</b>：turbo 的 {@code RailPositionCacheMixin} 把
 * {@code Rail.getPosition(progress)} 换成「0.25 格步长预采样 + 线性插值」。
 * 该 mixin 在 1.1.8 之前<b>从未生效</b>（handler 参数用 Object 占位 ⇒
 * 描述符不匹配 ⇒ 静默失效），所以它的插值误差从未被验证过。
 * 1.1.8 修好后它真正生效 ⇒ 必须证明它与上游逐点等价（或量化误差）。
 *
 * <p><b>怎么验证</b>：直接调用上游 {@code Rail.getPosition(double)}，
 * 与 turbo 的插值算法逐点对比（插值算法逐字照抄自 RailPositionCacheMixin）。
 *
 * <p><b>用法</b>（在 turbo 工程的 gradle 任务里跑，classpath 已含 MC 与 YMTR）：
 * <pre>
 *   java RailPositionParity
 * </pre>
 */
public class RailPositionParity {

	/** turbo 的步长（与 RailPositionCacheMixin.STEP 一致） */
	private static final double STEP = 0.25D;

	/** 判定阈值：0.25 格步长线性插值的单段误差上界约 0.125 格，取 0.5 格留余量 */
	private static final double TOLERANCE = 0.5D;

	public static void main(String[] args) throws Exception {
		final Method getPosition = Rail.class.getMethod("getPosition", double.class);
		final Method getLength = Rail.class.getMethod("getLength");

		double maxAbsDiff = 0.0;
		int violations = 0;
		int total = 0;
		int cases = 0;

		final RailAngle[] angles = RailAngle.values();
		for (final RailAngle a1 : angles) {
			for (final RailAngle a2 : angles) {
				if ((cases++ % 5) != 0) continue;   // 抽样，避免组合爆炸
				final Rail rail;
				try {
					rail = new Rail(new BlockPos(0, 0, 0), a1, new BlockPos(8, 0, 0), a2,
							RailType.IRON, TransportMode.TRAIN);
				} catch (final Throwable t) {
					continue;   // 几何非法（零长等）——跳过
				}
				final double len = (Double) getLength.invoke(rail);
				if (!(len > 0.01) || !Double.isFinite(len)) continue;

				// turbo 的预采样（逐字照抄 buildSamples）
				final int n = Math.max(2, (int) Math.ceil(len / STEP) + 1);
				final Vec3[] samples = new Vec3[n];
				for (int i = 0; i < n; i++) {
					final double d = Math.min(i * STEP, len);
					samples[i] = (Vec3) getPosition.invoke(rail, d);
				}

				for (int k = 0; k <= 200; k++) {
					final double progress = len * k / 200.0;
					final Vec3 expect = (Vec3) getPosition.invoke(rail, progress);
					final Vec3 actual = interpolate(samples, progress);
					final double dx = expect.x - actual.x;
					final double dy = expect.y - actual.y;
					final double dz = expect.z - actual.z;
					final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
					total++;
					if (dist > maxAbsDiff) maxAbsDiff = dist;
					if (dist > TOLERANCE) {
						violations++;
						if (violations <= 5) {
							System.out.printf("  x len=%.3f progress=%.4f diff=%.6f expect=(%.4f,%.4f,%.4f) actual=(%.4f,%.4f,%.4f)%n",
									len, progress, dist, expect.x, expect.y, expect.z, actual.x, actual.y, actual.z);
						}
					}
				}
			}
		}

		System.out.println();
		System.out.println("=== 汇总 ===");
		System.out.println("对拍点: " + total + "   超容差(>" + TOLERANCE + "格): " + violations
				+ "   maxAbsDiff: " + maxAbsDiff + " 格");
		if (violations > 0) {
			System.out.println("!! 缓存插值与上游 getPosition 存在显著偏差 ⇒ 不能默认开启 U6");
			System.exit(1);
		}
		System.out.println("OK 缓存插值与上游 getPosition 等价（误差 < " + TOLERANCE + " 格）");
	}

	/** turbo 缓存的插值算法（逐字照抄 RailPositionCacheMixin） */
	private static Vec3 interpolate(Vec3[] samples, double progress) {
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
}
