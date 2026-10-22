package mtrultra.mixin;

import mtrultra.MtrUltraConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * [U3] 轨道去重表零分配化（Forge 版）。
 * <p>
 * 原版 RenderTrains.render 每帧：
 * new HashMap<UUID, RailType> renderedRailMap → 每条轨道 new UUID（两个
 * posLong 打包）→ containsKey/get/put。大存档数千条轨 × 60FPS 的分配与
 * 装箱压力可观，是稳定 GC 毛刺来源。
 * <p>
 * 最终方案（务实）：U3 在 mixin 层面收益/风险比最差——render 方法体
 * 巨大、局部逻辑耦合紧，任何字节码级替换都脆弱。将其降级为：
 * "每帧复用 renderedRailMap 容器"——Redirect Map 构造器调用
 * new HashMap → 复用实例。仅此一处，安全且消除了每帧最大的一次性分配。
 * 其余 per-rail UUID 分配留待 YMTR 上游演进（已记录于 README 的
 * "未实施项"）。
 * <p>
 * Forge 版差异：目标方法为静态 4 参 render，第一参数是 mtr.entity.EntitySeat
 * （javap 验证：public static void render(mtr.entity.EntitySeat, float,
 * com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.MultiBufferSource)），
 * 官方名映射下写全签名区分实例/静态两个重载。
 */
@Mixin(targets = "mtr.render.RenderTrains", remap = false)
public abstract class RenderTrainsMixin {

	private static final String RENDER_STATIC =
			"render(Lmtr/entity/EntitySeat;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V";

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
			// [修复 2026-10-22] 与 fabric 版同步加 require=0：两者必须同生共死
			// （若 @Redirect 成功而 @Inject 失效会导致去重表跨帧残留）。
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
			// [修复 2026-10-22] 与 fabric 版同步加 require=0（@Redirect 不带
			// ordinal 时要求方法内唯一匹配；MTR 升级后签名变化会失败 ⇒ 崩客户端）。
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
