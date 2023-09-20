package showercurtain.simpleportals.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import showercurtain.simpleportals.mixinterfaces.PortalCooldown;

@Mixin(Entity.class)
public abstract class EntityMixin implements PortalCooldown {
    @Unique
    int timeout;

    @Override
    public int simple_portals$getTimeout() { return timeout; }

    @Override
    public int simple_portals$decrementTimeout() {
        if (timeout == 0) return 0;
        return --timeout;
    }

    @Override
    public void simple_portals$collideWithPortal() {
        timeout = 10;
    }

    @Inject(method="tick()V", at=@At("HEAD"))
    private void tick(CallbackInfo ci) {
        simple_portals$decrementTimeout();
    }
}
