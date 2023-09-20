package showercurtain.simpleportals;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.*;
import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import showercurtain.simpleportals.mixin.EntityMixin;

public class PortalBlock extends Block implements PolymerBlock {
    public enum PortalDeco implements StringIdentifiable {
        AIR("air", Blocks.AIR),
        WATER("water", Blocks.WATER),
        LAVA("lava", Blocks.LAVA),
        END_GATEWAY("end_gateway", Blocks.END_GATEWAY),
        POWDERED_SNOW("powdered_snow", Blocks.POWDER_SNOW),
        COBWEB("cobweb", Blocks.COBWEB);
        public final String name;
        public final Block display;

        PortalDeco(String name, Block display) { this.name = name; this.display = display; }

        @Override
        public String asString() {
            return this.name;
        }
    }

    public enum PortalDirection implements StringIdentifiable {
        NONE("none"),
        UP("up"),
        NORTH("north"),
        EAST("east"),
        SOUTH("south"),
        WEST("west"),
        DOWN("down"),
        ;

        public final String name;
        PortalDirection(String name) { this.name = name; }

        @Override
        public String asString() { return this.name; }

        public BlockPos toPos(BlockPos relative) {
            return switch (this) {
                case NONE -> relative.toImmutable();
                case UP -> relative.up();
                case DOWN -> relative.down();
                case NORTH -> relative.north();
                case EAST -> relative.east();
                case SOUTH -> relative.south();
                case WEST -> relative.west();
            };
        }
    }

    public PortalBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(DISABLED, false));
        setDefaultState(getDefaultState().with(DISPLAY, PortalDeco.AIR));
        setDefaultState(getDefaultState().with(DIRECTION, PortalDirection.NONE));
    }

    public static final BooleanProperty DISABLED = BooleanProperty.of("disabled");
    public static final EnumProperty<PortalDeco> DISPLAY = EnumProperty.of("display", PortalDeco.class);
    public static final EnumProperty<PortalDirection> DIRECTION = EnumProperty.of("direction", PortalDirection.class);

    @Override
    public Block getPolymerBlock(BlockState state) {
        if (state.get(DISABLED)) return Blocks.BEDROCK;
        else return state.get(DISPLAY).display;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(DISABLED);
        builder.add(DISPLAY);
        builder.add(DIRECTION);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (state.get(DISABLED)) return;
        if (world.isClient() || !entity.canUsePortals()) return;
        if (entity.simple_portals$getTimeout() > 0) {
            entity.simple_portals$collideWithPortal();
            return;
        }
        entity.simple_portals$collideWithPortal();
        PortalLink tmp = SimplePortalsMod.getPortal(pos, (ServerWorld) world);

        if (tmp == null) return;
        tmp.tp(entity);
    }

    @Override
    public void onBroken(WorldAccess world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
    }
}
