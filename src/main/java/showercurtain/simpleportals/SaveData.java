package showercurtain.simpleportals;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SaveData extends PersistentState {
    public ConcurrentHashMap<Identifier, ConcurrentHashMap<Long, PortalLink>> links = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, Pair<Identifier, Long>> names = new ConcurrentHashMap<>();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        if (links.isEmpty()) return nbt;
        NbtCompound out = new NbtCompound();
        links.forEach((id, link) -> {
            if (link.isEmpty()) return;
            NbtCompound dim = new NbtCompound();
            link.forEach((pos, link2) -> {
                dim.put(""+pos, link2.toNbt());
            });
            out.put(id.toString(), dim);
        });
        nbt.put("links", out);
        NbtCompound out2 = new NbtCompound();
        names.forEach((name, p) -> {
            NbtCompound tmp = new NbtCompound();
            tmp.putString("dim", p.getLeft().toString());
            tmp.putLong("pos", p.getRight());
            out2.put(name, tmp);
        });
        nbt.put("names", out2);

        return nbt;
    }

    public static SaveData fromNbt(NbtCompound nbt) {
        NbtCompound in = nbt.getCompound("links");
        SaveData ret = new SaveData();
        for (String key : in.getKeys()) {
            NbtCompound dim = in.getCompound(key);
            ConcurrentHashMap<Long, PortalLink> out = new ConcurrentHashMap<>();
            for (String pos : dim.getKeys()) {
                out.put(Long.parseLong(pos), PortalLink.fromNbt(dim.getCompound(pos)));
            }
            ret.links.put(new Identifier(key), out);
        }
        NbtCompound in2 = nbt.getCompound("names");
        for (String name : in2.getKeys()) {
            NbtCompound tmp = in2.getCompound(name);
            ret.names.put(name, new Pair<>(new Identifier(tmp.getString("dim")), tmp.getLong("pos")));
        }
        return ret;
    }

    public static SaveData fromServer(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();

        return manager.getOrCreate(
                SaveData::fromNbt,
                SaveData::new,
                "simpleportals");
    }

    public void registerLink(BlockPos at, Identifier in, PortalLink to) {
        if (!links.containsKey(in)) links.put(in, new ConcurrentHashMap<>());
        ConcurrentHashMap<Long, PortalLink> tmp = links.get(in);
        tmp.put(at.asLong(), to);
    }

    @Nullable
    public PortalLink getLink(Identifier in, BlockPos at) {
        if (in == null || at == null) return null;
        long at2 = at.asLong();
        if (!links.containsKey(in)) return null;
        ConcurrentHashMap<Long, PortalLink> tmp = links.get(in);
        if (!tmp.containsKey(at2)) return null;
        return tmp.get(at2);
    }

    @Nullable
    public PortalLink getLink(String name) {
        if (!names.containsKey(name)) return null;
        Pair<Identifier, Long> p = names.get(name);
        PortalLink out = getLink(p.getLeft(), BlockPos.fromLong(p.getRight()));
        if (out == null) names.remove(name);
        return out;
    }
}