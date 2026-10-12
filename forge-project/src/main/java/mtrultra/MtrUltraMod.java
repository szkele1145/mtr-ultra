package mtrultra;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MTR-Ultra 公共入口逻辑（与加载器无关的部分集中在静态方法里，供 Forge 入口点调用）。
 */
public class MtrUltraMod {

	public static final Logger LOGGER = LogManager.getLogger("MTR-Ultra");

	public static void onInitialize() {
		LOGGER.info("MTR-Ultra initializing");
		MtrUltraConfig.load();
	}
}
