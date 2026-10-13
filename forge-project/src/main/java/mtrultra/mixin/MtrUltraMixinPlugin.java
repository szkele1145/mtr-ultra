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
 * 检测方式（主→辅）：
 * 1. <b>类路径特征探测</b>（主）：尝试 Class.forName 加载 ANTE 的入口类。
 *    mixin 配置期 knot/mod classloader 已组装完毕，探测 100% 可用，
 *    不依赖任何 Forge 内部状态的初始化时机；
 * 2. ModList.get().isLoaded（辅）：Forge 原生 mod 列表，但它在 mixin
 *    配置期可能尚未初始化（返回 null 或抛异常）——只作加分项，
 *    检测不到不影响 1 的结论。
 * <p>
 * 保守方向：两级都失败时<b>不跳过</b>（放行 U5）——绝大多数用户不装 ANTE，
 * 放行是正确行为；装了 ANTE 而 1 级探测也失效的场景实际不存在
 * （ANTE 的 jar 在 classpath 上，其入口类必然可探测）。
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
		return null;
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
		// 主检测：类路径特征（mixin 期 100% 可用，不依赖 Forge 内部状态）
		try {
			Class.forName(ANTE_ENTRY_CLASS, false, MtrUltraMixinPlugin.class.getClassLoader());
			return true;
		} catch (final ClassNotFoundException expected) {
			// ANTE 不在场，走辅助检测
		} catch (final Throwable t) {
			// 类存在但初始化异常 — 视为在场（保守跳过 U5）
			return true;
		}
		// 辅助检测：Forge ModList（可能未初始化，失败不影响结论）
		try {
			return net.minecraftforge.fml.ModList.get().isLoaded(ANTE_MODID);
		} catch (final Throwable t) {
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
