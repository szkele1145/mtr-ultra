package mtrultra.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * [0.2.5] Mixin 配置插件：检测 ANTE（mtrsteamloco）在场时跳过 U5 的
 * RailwayDataMixin —— ANTE 的同名 mixin 整体覆写了 simulateTrains
 * （含其自身的轨道遍历优化），任何后续对该方法的注入都会在
 * PREINJECT Applicator 阶段被硬拒绝（"merged by"），require=0 也无法
 * 压制（三方共存实测两次确认）。
 * <p>
 * U5 的排班节流在 ANTE 环境整体让位：ScheduleRebuildFlag 保持默认
 * TRUE = 每 tick 重建，与原版行为一致；U1-U4 全部照常（不同目标类，
 * 与 ANTE 的 RailwayDataMixin 无交集）。
 * <p>
 * 三方实测（2026-10-13，YMTR 3.6.4.2 + ANTE 1.1.1-continue.2 +
 * MTR-Ultra 0.2.5）：U5 跳过后三方共存通过。
 */
public class MtrUltraMixinPlugin implements IMixinConfigPlugin {

	private static final String CONFLICTING_MIXIN = "mtrultra.mixin.RailwayDataMixin";
	private static final String ANTE_ENTRY_CLASS = "cn.zbx1425.mtrsteamloco.Main";
	private static final String ANTE_MODID = "mtrsteamloco";

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null; // 使用配置文件里的 refmap
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (CONFLICTING_MIXIN.equals(mixinClassName) && isAnteLoaded()) {
			System.out.println("[MTR-Ultra] ANTE (mtrsteamloco) detected - U5 schedule throttle mixin (RailwayDataMixin) skipped to avoid simulateTrains overwrite conflict. U1-U4 remain fully active.");
			return false;
		}
		return true;
	}

	private static boolean isAnteLoaded() {
		// 主检测：类路径特征（mixin 期 knot classloader 已组装，100% 可用，
		// 与加载器内部状态无关；主类在则 ANTE 必在场）
		try {
			Class.forName(ANTE_ENTRY_CLASS, false, MtrUltraMixinPlugin.class.getClassLoader());
			return true;
		} catch (final ClassNotFoundException expected) {
			// ANTE 不在场，走辅助检测
		} catch (final Throwable t) {
			return true; // 类在但初始化异常 — 保守跳过
		}
		// 辅助检测：FabricLoader 的 mod 列表（理论上 mixin 期已可用，
		// 万一加载器实现差异导致异常，兜底 false = 放行——类路径探测
		// 是权威判据，走到这里说明 ANTE 确实不在）
		try {
			return FabricLoader.getInstance().isModLoaded(ANTE_MODID);
		} catch (final Throwable t) {
			return false;
		}
	}

	@Override
	public List<String> getMixins() {
		return null; // 无动态追加
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
