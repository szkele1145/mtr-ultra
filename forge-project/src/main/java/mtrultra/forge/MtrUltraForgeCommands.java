package mtrultra.forge;

import com.mojang.brigadier.CommandDispatcher;
import mtrultra.MtrUltraStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * [0.2.0 Forge] /mtrultra stats 命令（Forge 事件版）。
 * <p>
 * Forge 通过 RegisterCommandsEvent 在服务器启动时注册命令，
 * 无需 fabric-api 的 CommandRegistrationCallback。
 */
@Mod.EventBusSubscriber(modid = "mtr_turbo")
public final class MtrUltraForgeCommands {

	private MtrUltraForgeCommands() {
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		dispatcher.register(Commands.literal("mtrturbo")
				.then(Commands.literal("stats").requires(source -> source.hasPermission(2))
						.executes(context -> {
							context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(MtrUltraStats.report()));
							return 1;
						}))
				// [1.1.3] /mtrturbo dump — stats 快照写 config/mtrturbo-stats.json
				.then(Commands.literal("dump").requires(source -> source.hasPermission(2))
						.executes(context -> {
							final int count = MtrUltraStats.dumpJson();
							context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(
									count > 0 ? "MTR-Turbo: stats snapshot written to config/mtrturbo-stats.json (" + count + " counters)"
											: "MTR-Turbo: stats dump failed - check the log"));
							return count > 0 ? 1 : 0;
						})));
	}
}
