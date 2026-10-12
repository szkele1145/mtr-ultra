package mtrultra.forge;

import mtrultra.MtrUltraMod;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 主入口（mods.toml -> [[mods]] modId "mtr_ultra"）。
 * 构造函数在 mod 加载阶段被 FML 调用。
 * <p>
 * 注意：Forge 47 的 modId 规则 ^[a-z][a-z0-9_]{1,63}$ 不允许连字符，
 * 故 Forge 版 modId 用下划线（Fabric 版保持 mtr-ultra）。
 */
@Mod("mtr_ultra")
public class MtrUltraForge {

	public MtrUltraForge() {
		MtrUltraMod.onInitialize();
	}
}
