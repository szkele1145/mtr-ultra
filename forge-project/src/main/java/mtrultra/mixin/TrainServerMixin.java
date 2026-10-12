package mtrultra.mixin;

import mtrultra.MtrUltraConfig;
import mtrultra.util.ScheduleRebuildFlag;
import mtr.path.PathData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
 * [U4] 服务端模拟 LOD（Forge 版）。
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
 * 把 ticksElapsed 参数放大为 interval（@ModifyArg 参数捕获），
 * 使单次调用完成多 tick 积分。ticksElapsed 放大与本 mixin 分离，
 * 避免同方法双注入点的 target 匹配冲突。
 * <p>
 * Forge 版差异：无（handler 参数用官方名 Level/Player，与 Fabric 端
 * mojmap 编译环境一致；@Inject method 用简名 "simulateTrain"）。
 */
@Mixin(targets = "mtr.data.TrainServer", remap = false)
public abstract class TrainServerMixin {

	// ---- Train 继承链上的 protected 字段（TrainServer 视角可 shadow） ----
	@Shadow
	protected boolean isCurrentlyManual;

	@Shadow
	protected List<PathData> path;

	@Shadow
	protected abstract boolean isPlayerRiding(Player player);

	@Unique
	private int mtrultra$simCounter = 0;

	@Unique
	private int mtrultra$simInterval = 1;

	@Inject(method = "simulateTrain", at = @At("HEAD"), cancellable = true)
	private void mtrultra$simulationLod(
			Level world, float ticksElapsed, Object depot, Object dataCache,
			List<Map<UUID, Long>> trainPositions,
			@Coerce Map<Object, Set<Object>> trainsInPlayerRange,
			@Coerce Map<Long, List<Object>> schedulesForPlatform,
			@Coerce Map<Long, Map<Object, Object>> trainDelays,
			CallbackInfoReturnable<Boolean> cir) {
		if (!MtrUltraConfig.enableSimulationLod) {
			mtrultra$simInterval = 1;
			return; // 开关关闭：原版行为
		}

		// 手动/载客列车永不降频（载客判定：任一在线玩家乘坐）
		if (mtrultra$isManualOrRiding(world)) {
			mtrultra$simInterval = 1;
			mtrultra$simCounter = 0;
			return;
		}

		final double nearest2 = mtrultra$nearestPlayerDistSq(world);
		mtrultra$simInterval =
				nearest2 <= (double) MtrUltraConfig.simulationLodNearBlocks * MtrUltraConfig.simulationLodNearBlocks ? 1
				: nearest2 <= (double) MtrUltraConfig.simulationLodMidBlocks * MtrUltraConfig.simulationLodMidBlocks ? 2
				: 4;

		if (mtrultra$simInterval > 1 && ++mtrultra$simCounter < mtrultra$simInterval) {
			cir.setReturnValue(false); // 本 tick 跳过（无需 trainsToSync）
		} else {
			mtrultra$simCounter = 0;
			// 到达间隔：写入 ThreadLocal 供伴生 ModifyArg 放大父类调用的步长
			TrainServerTickScaleMixin.ULTRA_INTERVAL.set(mtrultra$simInterval);
			// 放行原方法
		}
	}

	/**
	 * [U5 伴生] 排程写入跳过：非重建 tick 时把 lambda$simulateTrain$8 的
	 * List.add 调用替换为 no-op。lambda$8 是排程 add 的唯一写入口
	 * （字节码：Map.get → new ScheduleEntry → List.add），
	 * 跳过 add 即跳过排程写入，配合 RailwayDataMixin 的 clear 节流
	 * 实现完整 U5（数据保留上一份，无空窗、无重复）。
	 * <p>
	 * Forge 版字节码已核实：lambda$simulateTrain$8(Map, long, long, TimeSegment)
	 * 体内 List.add 是唯一写入口，与 Fabric 版一致。
	 */
	@Redirect(
			method = "lambda$simulateTrain$8",
			at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z")
	)
	private boolean mtrultra$skipScheduleWrite(Object list, Object entry) {
		if (MtrUltraConfig.enableScheduleThrottle && !ScheduleRebuildFlag.isSet()) {
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
		if (isCurrentlyManual) {
			return true;
		}
		if (world == null) {
			return false;
		}
		for (final Player player : world.players()) {
			if (isPlayerRiding(player)) {
				return true;
			}
		}
		return false;
	}

	@Unique
	private double mtrultra$nearestPlayerDistSq(Level world) {
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
