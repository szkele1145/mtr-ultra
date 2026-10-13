package mtrultra.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * [0.2.5] Mixin 配置插件（Forge 版）：检测 ANTE（mtrsteamloco）在场时
 * 跳过 U5 的 RailwayDataMixin —— ANTE 的同名 mixin 整体覆写了
 * simulateTrains，任何后续注入都会被 PREINJECT Applicator 硬拒绝
 * （Fabric 三方实测两次确认；Forge 同理）。
 * <p>
 * U5 让位后：ScheduleRebuildFlag 保持默认 TRUE = 每 tick 重建（原版行为）；
 * U1-U4 全部照常。
 * <p>
 * Forge 下用 ModList 检测（无 FabricLoader）；类加载失败兜底为
 * 不跳过（与非 ANTE 环境一致）。
 */
public class MtrUltraMixinPlugin implements IMixinConfigPlugin {

	private static final String ANTE_MODID = "mtrsteamloco";
	private static final String CONFLICTING_MIXIN = "mtrultra.mixin.RailwayDataMixin";

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (CONFLICTING_MIXIN.equals(mixinClassName)) {
			if (isAnteLoaded()) {
				System.out.println("[MTR-Ultra] ANTE (mtrsteamloco) detected - U5 schedule throttle mixin (RailwayDataMixin) skipped to avoid simulateTrains overwrite conflict. U1-U4 remain fully active.");
				return false;
			}
		}
		return true;
	}

	private static boolean isAnteLoaded() {
		try {
			return net.minecraftforge.fml.ModList.get().isLoaded(ANTE_MODID);
		} catch (final Throwable t) {
			// ModList 在 mixin 配置期可能尚未初始化 — 兜底不跳过
			return false;
		}
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}
}
