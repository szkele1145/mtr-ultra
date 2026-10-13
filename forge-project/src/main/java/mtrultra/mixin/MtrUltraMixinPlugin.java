package mtrultra.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * [1.0.2] Mixin 配置插件（Forge 版）：检测 ANTE（mtrsteamloco）在场时
 * 跳过 U5 的 RailwayDataMixin。
 * <p>
 * ⚠️ Forge 三方实测（2026-10-13）推翻了 Fabric 的检测方案移植：
 * Forge 的 mod 类由 ModClassLoader 在稍后阶段装载，
 * mixin 配置期 Class.forName 探测不到 ANTE 主类 → 探测失败 →
 * U5 没被跳过 → 与 ANTE 的 simulateTrains 覆写冲突 → 启动崩溃。
 * <p>
 * Forge 正解：mixin 配置期唯一可靠的"ANTE 在场"信号是
 * <b>游戏目录 mods 文件夹里的 jar 文件名特征</b>——纯文件系统探测，
 * 不依赖任何类加载状态。扫描 mods 目录找含 "mtr-ante"/"mtrsteamloco"
 * 特征的 .jar 即可（Forge Dev 环境的 mods 目录 = run/mods）。
 * <p>
 * 检测顺序（主→辅）：
 * 1. mods 目录文件名扫描（主，Forge 下 100% 可用）；
 * 2. ModList（辅助，可能未初始化）；
 * 3. 都失败 → 不跳过（无 ANTE 用户正确）。
 */
public class MtrUltraMixinPlugin implements IMixinConfigPlugin {

	private static final String CONFLICTING_MIXIN = "mtrultra.mixin.RailwayDataMixin";
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
			System.out.println("[MTR-Turbo] ANTE (mtrsteamloco) detected - U5 schedule throttle mixin (RailwayDataMixin) skipped to avoid simulateTrains overwrite conflict. U1-U4 remain fully active.");
			return false;
		}
		return true;
	}

	private static boolean isAnteLoaded() {
		// 主检测：mods 目录文件名扫描（mixin 配置期文件系统已就绪，Forge 下 100% 可用）
		try {
			final java.nio.file.Path modsDir = java.nio.file.Paths.get("mods").toAbsolutePath().normalize();
			if (java.nio.file.Files.isDirectory(modsDir)) {
				try (final java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(modsDir)) {
					for (final java.nio.file.Path p : (Iterable<java.nio.file.Path>) files::iterator) {
						final String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
						if (name.endsWith(".jar") && (name.contains("mtr-ante") || name.contains("mtrsteamloco") || name.contains("mtr_ante"))) {
							return true;
						}
					}
				}
			}
		} catch (final Throwable ignored) {
			// 文件系统不可用 — 走辅助检测
		}
		// 辅助检测：Forge ModList（可能未初始化）
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
