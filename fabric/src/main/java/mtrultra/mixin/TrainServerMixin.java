package mtrultra.mixin;

import mtrultra.MtrUltraConfig;
import mtrultra.MtrUltraStats;
import mtrultra.util.ScheduleRebuildFlag;
import mtr.path.PathData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * [U4] 服务端模拟 LOD。
 * <p>
 * 原版 RailwayData.simulateTrains 每 tick 对全部列车全速模拟，大型存档中
 * 远端列车是服务端 MSPT 的主要消耗者。
 * <p>
 * LOD 分级（按车头与最近玩家 XZ 距离）：
 * - 近（≤nearBlocks，默认 128）：每 tick（原行为）
 * - 中（≤midBlocks，默认 256）：每 2 tick，ticksElapsed 放大为 2（积分等价）
 * - 远（>midBlocks）：每 4 tick，ticksElapsed 放大为 4
 * <p>
 * 安全性：
 * - 手动驾驶（isCurrentlyManual）与载有玩家的列车永不降频；
 * - 停靠计时按放大后的 ticksElapsed 累加，进站时刻不变；
 * - 防撞 isRailBlocked 检查间隔 ≤4 tick，对应位移 ≤5 格 < 最小轨段长，不漏检。
 * <p>
 * 实现：simulateTrain（8 参）HEAD 注入——未到间隔 return false（无同步需求）；
 * 到达间隔时通过 {@link mtrultra.mixin.TrainServerTickScaleMixin 伴生注入}
 * 把 ticksElapsed 参数放大为 interval（@ModifyVariable 参数捕获），
 * 使单次调用完成多 tick 积分。ticksElapsed 放大与本 mixin 分离，
 * 避免同方法双注入点的 target 匹配冲突。
 */
@Mixin(targets = "mtr.data.TrainServer", remap = false)
public abstract class TrainServerMixin {

	// ---- Train 继承链上的成员（TrainServer 视角可 shadow） ----
	// [修复 2026-10-21] 可见性必须与目标完全一致：MTR 的 mtr.data.Train 中
	//   isCurrentlyManual = protected
	//   path              = public final List<PathData>
	//   isPlayerRiding    = public final boolean (Player)
	// mixin 运行时若把 @Shadow 声明的可见性低于目标，会抛
	// "cannot reduce visibiliy of ... target method/field"（InvalidMixinException，
	// 直接崩服/崩客户端）。AP 只在编译期给 warning，运行期才炸——必须写对。
	// 可见性经 6 个目标 jar（MTR 3.2.2-hf1 / YMTR 3.6.x × fabric/forge）javap 逐一核对。
	//
	// ★ [1.1.8 真机修复] 这两个成员此前**从未定位成功**（每个客户端/服务端启动都报
	//   InvalidMixinException: @Shadow method isPlayerRiding ... was not located
	//   in the target class mtr.data.TrainServer / mtr.data.TrainClient）：
	//   Mixin 的 @Shadow 解析走 TargetClassContext.findMethod(Deque, desc)，
	//   只遍历**目标类自身**的 classNode.methods，**不查父类**；
	//   isPlayerRiding 与 path 都定义在父类 mtr.data.Train 上，TrainServer 只是继承。
	//   ⇒ 对应功能（模拟节流的手动/乘坐判定、就近玩家距离判定）此前是空转。
	//   修法：改用 @Mixin 到 mtr.data.Train 的 @Accessor（见 TrainAccessor）。
	//   目标签名经 6 个目标 jar（MTR 3.2.2-hf1 / YMTR 3.6.x × fabric/forge）
	//   逐一 javap 核实一致：
	//     protected boolean isCurrentlyManual
	//     public final List<PathData> path
	//     public final boolean isPlayerRiding(Player)
	@Unique
	private boolean mtrultra$isManual() {
		return ((TrainAccessor) this).mtrultra$isCurrentlyManual();
	}

	@Unique
	private int mtrultra$simCounter = 0;

	@Unique
	private int mtrultra$simInterval = 1;

	// [修复 2026-10-21] require = 0：MTR 升级导致签名变化时 LOD 仅失效
	//（全速模拟），不崩服务端。与 RailwayDataMixin 的降级策略一致。
	@Inject(method = "simulateTrain", at = @At("HEAD"), cancellable = true, require = 0)
	private void mtrultra$simulationLod(
			Level world, float ticksElapsed, Object depot, Object dataCache,
			List<Map<UUID, Long>> trainPositions,
			@Coerce Map<Object, Set<Object>> trainsInPlayerRange,
			@Coerce Map<Long, List<Object>> schedulesForPlatform,
			@Coerce Map<Long, Map<Object, Object>> trainDelays,
			CallbackInfoReturnable<Boolean> cir) {
		// [0.2.3] 所有提前返回路径必须同步重置 ThreadLocal，否则残留上一列车的
		// interval——开关关闭/手动载客列车会被按上一次的 4 倍步长模拟（真 bug）
		if (!MtrUltraConfig.enableSimulationLod) {
			mtrultra$simInterval = 1;
			TrainServerTickScaleMixin.ULTRA_INTERVAL.set(1);
			return; // 开关关闭：原版行为
		}

		// 手动/载客列车永不降频（载客判定：任一在线玩家乘坐）
		if (mtrultra$isManualOrRiding(world)) {
			mtrultra$simInterval = 1;
			mtrultra$simCounter = 0;
			TrainServerTickScaleMixin.ULTRA_INTERVAL.set(1);
			return;
		}

		final double nearest2 = mtrultra$nearestPlayerDistSq(world);
		mtrultra$simInterval =
				nearest2 <= (double) MtrUltraConfig.simulationLodNearBlocks * MtrUltraConfig.simulationLodNearBlocks ? 1
				: nearest2 <= (double) MtrUltraConfig.simulationLodMidBlocks * MtrUltraConfig.simulationLodMidBlocks ? 2
				: 4;

		if (mtrultra$simInterval > 1 && ++mtrultra$simCounter < mtrultra$simInterval) {
			MtrUltraStats.simTicksSkipped.incrementAndGet();
			cir.setReturnValue(false); // 本 tick 跳过（无需 trainsToSync）
		} else {
			mtrultra$simCounter = 0;
			// 到达间隔：写入 ThreadLocal 供伴生 ModifyArg 放大父类调用的步长
			TrainServerTickScaleMixin.ULTRA_INTERVAL.set(mtrultra$simInterval);
			// 放行原方法
		}
	}

	/**
	 * [U5 伴生] 排班写入跳过：非重建 tick 时把 lambda$simulateTrain$8 的
	 * List.add 调用替换为 no-op。lambda$8 是排班 add 的唯一写入口
	 * （字节码：Map.get → new ScheduleEntry → List.add），
	 * 跳过 add 即跳过排班写入，配合 RailwayDataMixin 的 clear 节流
	 * 实现完整 U5（数据保留上一份，无空窗、无重复）。
	 */
	@Redirect(
			method = "lambda$simulateTrain$8",
			at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"),
			// [修复 2026-10-22] 补 require=0：MTR 升级改 lambda 序号/签名时
			// 注入失败仅告警不崩（排班节流失效 = 每 tick 都写排班，行为退化但不崩）。
			require = 0
	)
	private boolean mtrultra$skipScheduleWrite(Object list, Object entry) {
		if (MtrUltraConfig.enableScheduleThrottle && !ScheduleRebuildFlag.isSet()) {
			MtrUltraStats.scheduleWritesSkipped.incrementAndGet();
			return true; // 非重建 tick：跳过写入（保留上一份数据）
		}
		@SuppressWarnings("unchecked")
		final boolean added = ((List<Object>) list).add(entry);
		return added;
	}

	/** 伴生注入读取的当前间隔（包内可见） */
	@Unique
	int mtrultra$currentInterval() {
		return MtrUltraConfig.enableSimulationLod ? mtrultra$simInterval : 1;
	}

	@Unique
	private boolean mtrultra$isManualOrRiding(Level world) {
		if (mtrultra$isManual()) {
			return true;
		}
		if (world == null) {
			return false;
		}
		for (final Player player : world.players()) {
			if (((TrainAccessor) this).mtrultra$isPlayerRiding(player)) {
				return true;
			}
		}
		return false;
	}

	@Unique
	private double mtrultra$nearestPlayerDistSq(Level world) {
		final List<PathData> path = ((TrainAccessor) this).mtrultra$getPath();
		if (world == null || path == null || path.isEmpty()) {
			return 0; // 无法定位时视为近距离（不降频，保守）
		}
		final Vec3 head;
		try {
			head = path.get(0).rail.getPosition(0);
		} catch (final Exception e) {
			return 0;
		}
		if (head == null) {
			return 0;
		}
		double nearest = Double.MAX_VALUE;
		for (final Player player : world.players()) {
			final double dx = player.getX() - head.x;
			final double dz = player.getZ() - head.z;
			final double d2 = dx * dx + dz * dz;
			if (d2 < nearest) {
				nearest = d2;
			}
		}
		return nearest;
	}
}
