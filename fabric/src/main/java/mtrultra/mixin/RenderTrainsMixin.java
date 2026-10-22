package mtrultra.mixin;

import mtrultra.MtrUltraConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * [U3] 轨道去重表零分配化。
 * <p>
 * 原版 RenderTrains.render 每帧：
 * new HashMap<UUID, RailType> renderedRailMap → 每条轨道 new UUID（两个
 * posLong 打包）→ containsKey/get/put。大存档数千条轨 × 60FPS 的分配与
 * 装箱压力可观，是稳定 GC 毛刺来源。
 * <p>
 * 实现：静态复用的 HashMap<Long, Object>——key 为 min^max*31 打包的
 * posLong（零分配），value 保留 RailType 引用（同一性比较语义不变）。
 * 每帧 render 入口 clear 一次复用容器。
 * <p>
 * 挂点选择：RenderTrains.render 是数千行大方法，直接注入其内部的
 * getRailProduct/put 调用点侵入面大；替代方案——保留原逻辑但拦截
 * PathData.getRailProduct 的静态调用，把返回的 UUID 换为打包 long 无法
 * 改变 Map 的 key 类型。
 * <p>
 * 因此 U3 采取最低侵入等效实现：不碰原方法体，而是在 render 入口 HEAD
 * 分配一个帧内复用的 HashMap 由 ThreadLocal 持有，并 Redirect
 * getRailProduct 调用使其查复用表…… 仍复杂。
 * <p>
 * 最终方案（务实）：U3 在 mixin 层面收益/风险比最差——render 方法体
 * 巨大、局部逻辑耦合紧，任何字节码级替换都脆弱。将其降级为：
 * "每帧复用 renderedRailMap 容器"——Redirect Map 构造器调用
 * new HashMap → 复用实例。仅此一处，安全且消除了每帧最大的一次性分配。
 * 其余 per-rail UUID 分配留待 YMTR 上游演进（已记录于 README 的
 * "未实施项"）。
 */
@Mixin(targets = "mtr.render.RenderTrains", remap = false)
public abstract class RenderTrainsMixin {

	// [修复 2026-10-22] 描述符第一参数必须是 Lmtr/entity/EntitySeat;
	// 原先写的 Lnet/minecraft/class_1297;（MC Entity）——Mixin 的 MemberInfo.matches
	// 对 desc 是 String.equals 精确比较（字节码证据），不做子类型兼容 ⇒
	// EntitySeat 虽是 Entity 子类，desc 仍永不匹配 ⇒ @Inject/@Redirect
	// 从 v0.1.0 起从未生效，且 @Inject 在 defaultRequire=1 下注入失败直接崩客户端。
	// 实际签名（mtr-fabric-1.18.2 / mtr-fabric-1.19.4 / ymtr-fabric-1.19.2 /
	// ymtr-fabric-1.20.1 四个目标 jar javap 验证一致）：
	//   render(Lmtr/entity/EntitySeat;FLnet/minecraft/class_4587;Lnet/minecraft/class_4597;)V
	private static final String RENDER_STATIC =
			"render(Lmtr/entity/EntitySeat;FLnet/minecraft/class_4587;Lnet/minecraft/class_4597;)V";

	@Unique
	private static final ThreadLocal<HashMap<UUID, Object>> MTRULTRA$REUSED_MAP = new ThreadLocal<>();

	/**
	 * render 入口：帧开始时清理复用容器（上一帧的去重表作废）。
	 * 显式静态版签名（4 参）——render 有实例/静态两个重载，
	 * 轨道渲染去重表只在静态版内。
	 */
	@Inject(
			method = RENDER_STATIC,
			at = @At("HEAD"),
			// [修复 2026-10-22] 与 @Redirect 的降级策略一致：MTR 升级改签名时
			// 注入失败仅告警不崩（require=0 下 U3 整体失效——若 @Redirect 成功
			// 而 @Inject 失效会导致去重表跨帧残留，故两者必须同生共死，
			// 见下方 @Redirect 的同款 require=0）。
			require = 0
	)
	private void mtrultra$resetReusedMap(Object entity, float tickDelta, Object matrices, Object vertexConsumers, CallbackInfo ci) {
		if (MtrUltraConfig.enableRailDedupNoAlloc) {
			final HashMap<UUID, Object> map = MTRULTRA$REUSED_MAP.get();
			if (map != null) {
				map.clear();
			}
		}
	}

	/**
	 * 替换静态 render 内的 new HashMap 调用为复用实例（同签名显式匹配）。
	 * 字节码确认：静态 render 内 new HashMap 仅轨道去重段一处。
	 * 复用实例避免每帧 HashMap 桶数组分配；内容生命周期由 HEAD 注入的
	 * clear 管理（同帧多次 render 清零也安全——去重表只在单次 render 内有效）。
	 */
	@org.spongepowered.asm.mixin.injection.Redirect(
			method = RENDER_STATIC,
			at = @At(value = "NEW", target = "Ljava/util/HashMap;<init>()V"),
			// [修复 2026-10-21] 原无 require，默认 require=1。
			// @Redirect 不带 ordinal 时要求目标方法内**唯一**匹配；
			// MTR 升级后只要多出一处 new HashMap()，注入就失败 ⇒ 直接崩客户端。
			// 降级为 require=0：注入失败时保留原版每帧 new，行为退化但不崩。
			// 与同工程 RailwayDataMixin 的「失败仅警告不崩溃」策略保持一致。
			require = 0
	)
	private Object mtrultra$reuseRailMap() {
		if (!MtrUltraConfig.enableRailDedupNoAlloc) {
			return new HashMap<>();
		}
		HashMap<UUID, Object> map = MTRULTRA$REUSED_MAP.get();
		if (map == null) {
			map = new HashMap<>();
			MTRULTRA$REUSED_MAP.set(map);
		}
		return map;
	}
}
