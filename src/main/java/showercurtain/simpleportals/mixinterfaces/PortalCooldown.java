package showercurtain.simpleportals.mixinterfaces;

public interface PortalCooldown {
    default int simple_portals$getTimeout() {
        return 1;
    }

    default int simple_portals$decrementTimeout() {
        return 1;
    }

    default void simple_portals$collideWithPortal() {
    }
}
