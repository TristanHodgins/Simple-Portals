package showercurtain.simpleportals;

import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.TeleportCommand;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

public class SimplePortalsMod implements ModInitializer, ServerLifecycleEvents.ServerStarted {
	public static SaveData links;

	public static final Block PORTAL_BLOCK = new PortalBlock(
			FabricBlockSettings.create()
					.strength(-1.0F, 3600000.0F)
					.dropsNothing()
					.allowsSpawning(SimplePortalsMod::never)
	);

    public static final Logger LOGGER = LoggerFactory.getLogger("simple-portals");

	public static Boolean never(BlockState state, BlockView world, BlockPos pos, EntityType<?> type) {
		return false;
	}

	@Override
	public void onInitialize() {
		Registry.register(Registries.BLOCK, new Identifier("simpleportals","portal_block"), PORTAL_BLOCK);
		CommandRegistrationCallback.EVENT.register(Commands::register);
		ServerLifecycleEvents.SERVER_STARTED.register(this);
	}

	@Override
	public void onServerStarted(MinecraftServer server) {
		links = SaveData.fromServer(server);
	}

	public static BlockPos getPortalPos(BlockPos pos, ServerWorld world) {
		if (world.getBlockState(pos).getBlock() != PORTAL_BLOCK) return null;
		PortalBlock.PortalDirection dir = world.getBlockState(pos).get(PortalBlock.DIRECTION);
		while (dir != PortalBlock.PortalDirection.NONE) {
			pos = dir.toPos(pos);
			dir = world.getBlockState(pos).get(PortalBlock.DIRECTION);
		}
		return pos;
	}

	@Nullable
	public static PortalLink getPortal(BlockPos pos, ServerWorld world) {
		return SimplePortalsMod.links.getLink(world.getRegistryKey().getValue(), getPortalPos(pos, world));
	}

	public static boolean delete(BlockPos pos, ServerWorld world) {
		if (world.getBlockState(pos).getBlock() != PORTAL_BLOCK) return false;
		ConcurrentHashMap<Long, PortalLink> tmp = links.links.get(world.getRegistryKey().getValue());
		PortalLink tmp2 = tmp.get(pos.asLong());
		tmp.remove(pos.asLong());
		links.names.remove(tmp2.name);
		deleteRec(getPortalPos(pos, world), world);
		return true;
	}

	public static void deleteRec(BlockPos pos, ServerWorld world) {
		if (world.getBlockState(pos).getBlock()!=PORTAL_BLOCK) return;
		world.setBlockState(pos, Blocks.AIR.getDefaultState());
		deleteRec(pos.east(), world);
		deleteRec(pos.south(), world);
		deleteRec(pos.up(), world);
		deleteRec(pos.west(), world);
		deleteRec(pos.north(), world);
		deleteRec(pos.down(), world);
	}

	public static void setToggle(BlockPos pos, ServerWorld world, boolean to) {
		BlockState s = world.getBlockState(pos);
		if (s.getBlock() != PORTAL_BLOCK || s.get(PortalBlock.DISABLED) == to) return;
		world.setBlockState(pos, s.with(PortalBlock.DISABLED, to));
		setToggle(pos.up(), world, to);
		setToggle(pos.down(), world, to);
		setToggle(pos.north(), world, to);
		setToggle(pos.east(), world, to);
		setToggle(pos.south(), world, to);
		setToggle(pos.west(), world, to);
	}

	public static boolean toggle(BlockPos pos, ServerWorld world) {
		if (world.getBlockState(pos).getBlock() != PORTAL_BLOCK) return false;
		setToggle(pos, world, !world.getBlockState(pos).get(PortalBlock.DISABLED));
		return true;
	}
}