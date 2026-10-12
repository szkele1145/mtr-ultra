package mtrultra.fabric;

import mtrultra.MtrUltraMod;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric 主入口点（fabric.mod.json -> entrypoints.main）。
 */
public class MtrUltraFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		MtrUltraMod.onInitialize();
	}
}
