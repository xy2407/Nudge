package com.xy2407.nudge.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.level.ServerLevel;

import com.xy2407.nudge.CollisionGrid;

/**
 * Server events that drive grid maintenance, entity tracking and cleanup
 */
public final class CommonEvents {

    private CommonEvents() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                CollisionGrid.tick(level);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> CollisionGrid.onEntityJoin(world, entity));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> CollisionGrid.onEntityLeave(world, entity));
        ServerWorldEvents.UNLOAD.register((server, world) -> CollisionGrid.forget(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> CollisionGrid.clear());
    }
}
