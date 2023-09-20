package showercurtain.simpleportals;

import com.mojang.brigadier.CommandDispatcher;

import com.mojang.brigadier.SingleRedirectModifier;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldedit.world.World;
import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.*;

public class Commands {
    private static final CommandSyntaxException NO_PORTAL_FOUND = new SimpleCommandExceptionType(Text.literal("That portal does not exist")).create();
    private static final CommandSyntaxException INCOMPLETE_SELECTION = new SimpleCommandExceptionType(Text.literal("Incomplete worldedit selection")).create();
    private static final CommandSyntaxException PORTAL_TOO_BIG = new SimpleCommandExceptionType(Text.literal("That portal is way too big")).create();
    private static final CommandSyntaxException NO_POSITION = new SimpleCommandExceptionType(Text.literal("Must specify target position")).create();
    private static final CommandSyntaxException NO_NAME = new SimpleCommandExceptionType(Text.literal("Must specify name")).create();
    private static final CommandSyntaxException WORLD_UNLOADED = new SimpleCommandExceptionType(Text.literal("That world is not loaded or does not exist")).create();
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, RegistrationEnvironment environment) {
        dispatcher.register(literal("portals").then(literal("create")));
        CommandNode<ServerCommandSource> createNode = dispatcher.findNode(Arrays.asList("portals","create"));
        dispatcher.register(literal("portals").then(literal("edit")));
        CommandNode<ServerCommandSource> editNode = dispatcher.findNode(Arrays.asList("portals","edit"));
        dispatcher.register(literal("portals").requires(Permissions.require("simpleportals.create", 2).or(Permissions.require("simpleportals.toggle")).or(Permissions.require("simpleportals.view")))
                .then(literal("create").requires(((Predicate<ServerCommandSource>)ServerCommandSource::isExecutedByPlayer).and(Permissions.require("simpleportals.create", 4)))
                        .then(literal("in")
                                .then(argument("destDimension", DimensionArgumentType.dimension()).redirect(createNode, setField("destDimension", Identifier.class))))
                        .then(literal("named")
                                .then(argument("name", StringArgumentType.string()).redirect(createNode, setField("name", String.class))))
                        .then(literal("at")
                                .then(argument("destPosition", Vec3ArgumentType.vec3()).redirect(createNode, setField("destPosition", PosArgument.class))))
                        .then(literal("rotate")
                                .then(argument("destRotation", Vec2ArgumentType.vec2()).redirect(createNode, setField("destRotation", PosArgument.class))))
                        .then(literal("allowMobs")
                                .then(argument("allowMobs", BoolArgumentType.bool()).redirect(createNode, setField("allowMobs", Boolean.class))))
                        .then(literal("block")
                                .then(argument("block", StringArgumentType.word()).suggests(Commands::suggestBlocks).redirect(createNode, setField("block", String.class))))
                        .then(literal("perm")
                                .then(argument("perm", StringArgumentType.string()).redirect(createNode, setField("perm", String.class))))
                        .then(literal("finish").executes(Commands::create)))
                .then(literal("edit").requires(((Predicate<ServerCommandSource>)ServerCommandSource::isExecutedByPlayer).and(Permissions.require("simpleportals.create", 2)))
                        .then(literal("named")
                                .then(argument("name", StringArgumentType.string()).suggests(Commands::suggestNames).redirect(createNode, setField("name", String.class))))
                        .then(literal("in")
                                .then(argument("destDimension", DimensionArgumentType.dimension()).redirect(editNode, setField("destDimension", Identifier.class))))
                        .then(literal("at")
                                .then(argument("destPosition", Vec3ArgumentType.vec3()).redirect(editNode, setField("destPosition", PosArgument.class))))
                        .then(literal("rotate")
                                .then(argument("destRotation", Vec2ArgumentType.vec2()).redirect(editNode, setField("destRotation", PosArgument.class))))
                        .then(literal("allowMobs")
                                .then(argument("allowMobs", BoolArgumentType.bool()).redirect(editNode, setField("allowMobs", Boolean.class))))
                        .then(literal("perm")
                                .then(argument("perm", StringArgumentType.string()).redirect(editNode, setField("perm", String.class))))
                        .then(literal("finish").executes(Commands::edit)))
                .then(literal("delete").requires(Permissions.require("simpleportals.create", 4))
                        .then(argument("portalName", StringArgumentType.word()).suggests(Commands::suggestNames).executes(Commands::delete)))
                .then(literal("toggle").requires(Permissions.require("simpleportals.toggle",2))
                        .then(argument("portalName", StringArgumentType.word()).suggests(Commands::suggestNames).executes(Commands::toggle)))
                .then(literal("nearest").requires(Permissions.require("simpleportals.view",2)).executes(Commands::getNearest))
                .then(literal("list").requires(Permissions.require("simpleportals.view", 2).and(ServerCommandSource::isExecutedByPlayer)).executes(Commands::list))
                .then(literal("view").requires(Permissions.require("simpleportals.view", 2))
                        .then(argument("portalName", StringArgumentType.word()).suggests(Commands::suggestNames).executes(Commands::view))));
    }

    private static int view(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String portal = ctx.getArgument("portalName", String.class);
        Pair<Identifier, Long> location = SimplePortalsMod.links.names.get(portal);
        if (location == null) throw NO_PORTAL_FOUND;
        PortalLink link = SimplePortalsMod.links.getLink(portal);
        String out = "Portal "+portal+", located in "+location.getLeft()+" at "+BlockPos.fromLong(location.getRight()).toShortString()+", ";
        if (link.onlyPlayers) out += "players only, ";
        else out += "mobs allowed, ";
        if (link.perm.isEmpty()) out += "no perms required";
        else out += "requires "+link.perm+" perm";
        String finalOut = out;
        ctx.getSource().sendFeedback(()->Text.literal(finalOut), false);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        for (Map.Entry<String, Pair<Identifier, Long>> i : SimplePortalsMod.links.names.entrySet()) {
            ctx.getSource().sendFeedback(()->Text.literal(i.getKey()+" ").append(Text.literal("[COPY]").setStyle(Style.EMPTY.withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, i.getKey())))).append(" in "+i.getValue().getLeft()+" at "+BlockPos.fromLong(i.getValue().getRight()).toShortString()), false);
        }
        return 1;
    }

    private static int edit(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        String name = src.simple_portals$getField("name", String.class);
        if (name == null) throw NO_NAME;
        PortalLink out = SimplePortalsMod.links.getLink(name);
        if (out == null) throw NO_PORTAL_FOUND;

        Identifier world = src.simple_portals$getField("destDimension", Identifier.class);
        if (world != null) out.to = world;
        PosArgument pos = src.simple_portals$getField("destPosition", PosArgument.class);
        if (pos != null) {
            Vec3d pos2 = pos.toAbsolutePos(src);
            out.x = pos2.x;
            out.y = pos2.y;
            out.z = pos2.z;
        }
        PosArgument rot = src.simple_portals$getField("destRotation", PosArgument.class);
        if (rot != null) {
            Vec2f rot2 = rot.toAbsoluteRotation(src);
            out.changeRotation = true;
            out.yaw = rot2.y;
            out.pitch = rot2.x;
        }
        Boolean mobs = src.simple_portals$getField("allowMobs", Boolean.class);
        if (mobs != null) out.onlyPlayers = !mobs;
        String perm = src.simple_portals$getField("perm", String.class);
        if (perm != null) out.perm = perm;

        ctx.getSource().sendFeedback(()->Text.literal("Edited portal "+name), true);

        return 1;
    }

    private static <T> SingleRedirectModifier<ServerCommandSource> setField(String name, Class<T> type) {
        return ctx -> {
            ctx.getSource().simple_portals$setField(name, ctx.getArgument(name, type));
            return ctx.getSource();
        };
    }

    private static int getNearest(CommandContext<ServerCommandSource> ctx) {
        AtomicReference<PortalLink> nearest = new AtomicReference<>();
        AtomicReference<Double> distance = new AtomicReference<>(Double.MAX_VALUE);
        AtomicReference<BlockPos> pos2 = new AtomicReference<>();
        SimplePortalsMod.links.links.get(ctx.getSource().getWorld().getRegistryKey().getValue()).forEach((pos, link) -> {
            double newDist = ctx.getSource().getPosition().squaredDistanceTo(BlockPos.fromLong(pos).toCenterPos());
            if (distance.get() >newDist) {
                nearest.set(link);
                distance.set(newDist);
                pos2.set(BlockPos.fromLong(pos));
            }
        });
        PortalLink near = nearest.get();
        if (near == null) ctx.getSource().sendFeedback(()->Text.literal("There are no portals in your dimension"), false);
        else {
            ctx.getSource().sendFeedback(()->
                    Text.literal("Portal named "+near.name+" ")
                            .append(Text.literal("[COPY]")
                                    .setStyle(Style.EMPTY
                                            .withBold(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, near.name))))
                            .append(" at "+pos2.get().toShortString()+" goes to "+near.x+" "+near.y+" "+near.z+" in "+near.to), false);
        }
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestBlocks(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (PortalBlock.PortalDeco s : PortalBlock.PortalDeco.values()) {
            builder.suggest(s.name);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestNames(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        for (String s : SimplePortalsMod.links.names.keySet()) {
            builder.suggest(s);
        }
        return builder.buildFuture();
    }

    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        String name = src.simple_portals$getField("name", String.class);
        if (name == null) throw NO_NAME;
        Identifier world = src.simple_portals$getField("destDimension", Identifier.class);
        if (world == null) world = src.getWorld().getRegistryKey().getValue();
        PosArgument pos = src.simple_portals$getField("destPosition", PosArgument.class);
        if (pos == null) throw NO_POSITION;
        Vec3d pos2 = pos.toAbsolutePos(src);
        PosArgument rot = src.simple_portals$getField("destRotation", PosArgument.class);
        Vec2f rot2 = rot != null ? rot.toAbsoluteRotation(src) : null;
        Boolean mobs = src.simple_portals$getField("allowMobs", Boolean.class);
        if (mobs == null) mobs = false;
        PortalBlock.PortalDeco block = PortalBlock.PortalDeco.AIR;
        String blockName = src.simple_portals$getField("block", String.class);
        if (blockName != null) {
            for (PortalBlock.PortalDeco d : PortalBlock.PortalDeco.values()) {
                if (d.name.equals(blockName)) {
                    block = d;
                    break;
                }
            }
        }
        String perm = src.simple_portals$getField("perm", String.class);

        Player actor = FabricAdapter.adaptPlayer(ctx.getSource().getPlayer());
        SessionManager manager = WorldEdit.getInstance().getSessionManager();
        LocalSession session = manager.get(actor);
        Region region;
        World selectionWorld = session.getSelectionWorld();
        try {
            if (selectionWorld == null) throw new IncompleteRegionException();
            region = session.getSelection(selectionWorld);
        } catch (IncompleteRegionException ex) {
            throw INCOMPLETE_SELECTION;
        }

        BlockPos corner = FabricAdapter.toBlockPos(region.iterator().next());

        Queue<Pair<BlockPos, PortalBlock.PortalDirection>> blockUpdates = new ArrayDeque<>();
        ServerWorld w = src.getWorld();
        w.setBlockState(corner, SimplePortalsMod.PORTAL_BLOCK.getDefaultState().with(PortalBlock.DISPLAY, block));
        Consumer<BlockPos> addBlocks = p -> {
            blockUpdates.add(new Pair<>(p.up(), PortalBlock.PortalDirection.DOWN));
            blockUpdates.add(new Pair<>(p.down(), PortalBlock.PortalDirection.UP));
            blockUpdates.add(new Pair<>(p.north(), PortalBlock.PortalDirection.SOUTH));
            blockUpdates.add(new Pair<>(p.east(), PortalBlock.PortalDirection.WEST));
            blockUpdates.add(new Pair<>(p.south(), PortalBlock.PortalDirection.NORTH));
            blockUpdates.add(new Pair<>(p.west(), PortalBlock.PortalDirection.EAST));
        };
        addBlocks.accept(corner);
        while (!blockUpdates.isEmpty()) {
            Pair<BlockPos, PortalBlock.PortalDirection> p = blockUpdates.remove();
            if (w.getBlockState(p.getLeft()).getBlock() == SimplePortalsMod.PORTAL_BLOCK || !region.contains(FabricAdapter.adapt(p.getLeft()))) continue;
            w.setBlockState(p.getLeft(), SimplePortalsMod.PORTAL_BLOCK.getDefaultState().with(PortalBlock.DISPLAY, block).with(PortalBlock.DIRECTION, p.getRight()));
            addBlocks.accept(p.getLeft());
        }

        PortalLink out;
        if (rot == null) {
            out = new PortalLink(pos2.x, pos2.y, pos2.z, world, !mobs, perm, name);
        } else {
            out = new PortalLink(pos2.x, pos2.y, pos2.z, rot2.y, rot2.x, world, !mobs, perm, name);
        }

        SimplePortalsMod.links.names.put(name, new Pair<>(ctx.getSource().getWorld().getRegistryKey().getValue(), corner.asLong()));

        SimplePortalsMod.links.registerLink(corner, ctx.getSource().getWorld().getRegistryKey().getValue(), out);
        SimplePortalsMod.links.markDirty();
        ctx.getSource().sendFeedback(()->Text.literal("Created portal at "+corner), true);

        return 1;
    }

    private static int delete(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Pair<Identifier, Long> portalId = SimplePortalsMod.links.names.get(ctx.getArgument("portalName", String.class));
        if (portalId == null) throw NO_PORTAL_FOUND;
        ServerWorld world = ctx.getSource().getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, portalId.getLeft()));
        BlockPos pos = BlockPos.fromLong(portalId.getRight());
        if (world == null) throw WORLD_UNLOADED;
        SimplePortalsMod.delete(pos, world);
        ctx.getSource().sendFeedback(()->Text.literal("Deleted portal at "+pos.toShortString()), true);
        SimplePortalsMod.links.markDirty();
        return 1;
    }

    private static int toggle(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Pair<Identifier, Long> portalId = SimplePortalsMod.links.names.get(ctx.getArgument("portalName", String.class));
        if (portalId == null) throw NO_PORTAL_FOUND;
        ServerWorld world = ctx.getSource().getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, portalId.getLeft()));
        BlockPos pos = BlockPos.fromLong(portalId.getRight());
        if (world == null) throw WORLD_UNLOADED;
        SimplePortalsMod.toggle(pos, world);
        ctx.getSource().sendFeedback(()->Text.literal("Toggled portal at "+pos.toShortString()), true);
        SimplePortalsMod.links.markDirty();
        return 1;
    }
}
