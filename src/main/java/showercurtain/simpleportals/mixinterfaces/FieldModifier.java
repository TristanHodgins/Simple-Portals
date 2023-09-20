package showercurtain.simpleportals.mixinterfaces;

import org.jetbrains.annotations.Nullable;

public interface FieldModifier {
    default void simple_portals$setField(String name, Object value) { };

    @Nullable
    default <T> T simple_portals$getField(String name, Class<T> type) { return null; };
}
