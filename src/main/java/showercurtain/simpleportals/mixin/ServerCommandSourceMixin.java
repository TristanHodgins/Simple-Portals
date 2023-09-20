package showercurtain.simpleportals.mixin;

import java.util.HashMap;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Unique;
import showercurtain.simpleportals.SimplePortalsMod;
import showercurtain.simpleportals.mixinterfaces.FieldModifier;

@Mixin(ServerCommandSource.class)
public abstract class ServerCommandSourceMixin implements FieldModifier {
    @Unique
    private HashMap<String, Object> fields = null;

    @Override
    public void simple_portals$setField(String name, Object value) {
        if (fields == null) fields = new HashMap<>();
        fields.put(name, value);
    }

    @Override
    public <T> T simple_portals$getField(String name, Class<T> type) {
        if (fields == null) { return null; }
        Object out = fields.get(name);
        return (T) out;
    }
}
