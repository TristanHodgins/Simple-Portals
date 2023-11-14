package showercurtain.simpleportals;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NoteBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.TeleportCommand;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Position;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashSet;
import java.util.Objects;

public class PortalLink {
    boolean onlyPlayers;
    double x;
    double y;
    double z;
    float yaw;
    float pitch;
    Identifier to; // If using my fantasy frontend mod, worlds might not be initialized
    boolean changeRotation;

    String perm;
    String name;

    public PortalLink(double x, double y, double z, float yaw, float pitch, Identifier to, boolean onlyPlayers, @Nullable String perm, String name) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.to = to;
        this.onlyPlayers = onlyPlayers;
        if (perm == null) this.perm = "";
        else this.perm = perm;
        this.changeRotation = true;
        if (name == null) this.name = "";
        else this.name = name;
    }

    public PortalLink(double x, double y, double z, Identifier to, boolean onlyPlayers, @Nullable String perm, String name) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.changeRotation = false;
        this.to = to;
        this.onlyPlayers = onlyPlayers;
        if (perm == null) this.perm = "";
        else this.perm = perm;
        if (name == null) this.name = "";
        else this.name = name;
    }

    public void tp(Entity entity) {
        if (onlyPlayers && !entity.isPlayer()) return;
        if (!(perm.isEmpty() || Permissions.check(entity, perm))) return;
        MinecraftServer server = entity.getServer();
        if (server == null) return;
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, to));
        if (world == null) return;
        BlockPos blockPos = BlockPos.ofFloored(x, y, z);
        if (!World.isValid(blockPos)) return;
        float f = MathHelper.wrapDegrees(yaw);
        float g = MathHelper.wrapDegrees(pitch);
        if (!changeRotation) {
            f = entity.getHeadYaw();
            g = entity.getPitch();
        }
        if (entity.teleport(world, 0, 10000, 0, new HashSet<>(), f, g)) {
            entity.requestTeleport(x, y, z);
            if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isFallFlying()) {
                entity.setVelocity(entity.getVelocity().multiply(1.0, 0.0, 1.0));
                entity.setOnGround(true);
            }

            if (entity instanceof PathAwareEntity pathAwareEntity) {
                pathAwareEntity.getNavigation().stop();
            }
        }
    }

    public NbtCompound toNbt() {
        NbtCompound out = new NbtCompound();
        out.putBoolean("onlyPlayers", onlyPlayers);
        out.putDouble("x", x);
        out.putDouble("y", y);
        out.putDouble("z", z);
        out.putBoolean("rotate", changeRotation);
        if (changeRotation) {
            out.putFloat("yaw", yaw);
            out.putFloat("pitch", pitch);
        }
        out.putString("toWorld", to.toString());
        if (!perm.isEmpty()) out.putString("perm", perm);
        if (!name.isEmpty()) out.putString("name", name);
        return out;
    }

    public static PortalLink fromNbt(NbtCompound nbt) {
        if (nbt.getBoolean("rotate")) return new PortalLink(
                nbt.getDouble("x"),
                nbt.getDouble("y"),
                nbt.getDouble("z"),
                nbt.getFloat("yaw"),
                nbt.getFloat("pitch"),
                new Identifier(nbt.getString("toWorld")),
                nbt.getBoolean("onlyPlayers"),
                nbt.getString("perm"),
                nbt.getString("name"));
        else return new PortalLink(
                nbt.getDouble("x"),
                nbt.getDouble("y"),
                nbt.getDouble("z"),
                new Identifier(nbt.getString("toWorld")),
                nbt.getBoolean("onlyPlayers"),
                nbt.getString("perm"),
                nbt.getString("name"));
    }
}
