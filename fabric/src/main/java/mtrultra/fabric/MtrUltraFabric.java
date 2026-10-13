package mtrultra.fabric;

import mtrultra.MtrUltraMod;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric 主入口点（fabric.mod.json -> entrypoints.main）。
 * <p>
 * stats 命令（/mtrturbo stats）按 fabric-api command 包可用性条件注册：
 * 1.18.2 的 fabric-api 0.77.0 无 command v2 包 —— 通过反射探测注册类，
 * 不存在时静默跳过（1.18.2 构建 sourceSets 已排除 MtrUltraCommands，
 * 但本类被全部版本共用，故用反射而非直接调用）。
 */
public class MtrUltraFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		MtrUltraMod.onInitialize();
		try {
			Class.forName("mtrultra.fabric.MtrUltraCommands", false, getClass().getClassLoader())
					.getMethod("register").invoke(null);
		} catch (final ClassNotFoundException e) {
			// 1.18.2 构建不含命令类 — 静默跳过（stats 功能裁剪）
		} catch (final Exception e) {
			MtrUltraMod.LOGGER.warn("Failed to register /mtrturbo stats command", e);
		}
	}
}
