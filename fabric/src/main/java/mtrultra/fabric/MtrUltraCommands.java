package mtrultra.fabric;

import com.mojang.brigadier.CommandDispatcher;
import mtrultra.MtrUltraStats;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * [0.2.0] /mtrultra stats 命令：输出各优化实时命中数与估算节省。
 * 需要权限等级 2（单人作弊开启或服务器 OP）。
 * <p>
 * 编译环境说明：fabric-api 在本编译 classpath 中保持 intermediary 名
 * （CommandRegistrationCallback.register 的参数是 class_2168/7157/2170$5364），
 * 因此 register 的 lambda 参数全部用 Object 接收（显式绕过泛型匹配），
 * dispatcher 参数保留 CommandDispatcher<CommandSourceStack> 原始泛型——
 * 该类型来自 Brigadier（非 MC 类，双端一致），命令树泛型推断正常。
 */
public final class MtrUltraCommands {

	private MtrUltraCommands() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			((CommandDispatcher<CommandSourceStack>) dispatcher).register(Commands.literal("mtrturbo")
					.then(Commands.literal("stats").requires(source -> source.hasPermission(2))
							.executes(context -> {
								context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(MtrUltraStats.report()));
								return 1;
							}))
					// [1.1.3] /mtrturbo dump — 把 stats 快照写入 config/mtrturbo-stats.json
					// 便于社区分享标准化基准数据（含平台/版本元信息）
					.then(Commands.literal("dump").requires(source -> source.hasPermission(2))
							.executes(context -> {
								final int count = MtrUltraStats.dumpJson();
								context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(
										count > 0 ? "MTR-Turbo: stats snapshot written to config/mtrturbo-stats.json (" + count + " counters)"
												: "MTR-Turbo: stats dump failed - check the log"));
								return count > 0 ? 1 : 0;
							})));
		});
	}
}
