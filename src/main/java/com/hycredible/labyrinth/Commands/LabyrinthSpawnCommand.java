package com.hycredible.labyrinth.Commands;

import com.hycredible.labyrinth.LabyrinthState;
import com.hycredible.labyrinth.Prefabs.Placer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.jspecify.annotations.NonNull;

public class LabyrinthSpawnCommand extends CommandBase {

    // Blocks north and west of the prefab to be ignored d(outside wall/outside of labyrinth)
    private static final int OFFSET_NORTH = 0;
    private static final int OFFSET_WEST  = 0;

    // Labyrinth grid size in sections.
    private static final int SECTIONS_X = 7;
    private static final int SECTIONS_Z = 7;

    public LabyrinthSpawnCommand() {
        super("labyrinth:spawn", "Spawns a mysterious labyrinth");
        this.setPermissionGroups(String.valueOf(GameMode.Adventure));
    }

    @Override
    protected void executeSync(@NonNull CommandContext commandContext) {
        Ref<EntityStore> ref = commandContext.senderAsPlayerRef();
        if (ref == null) return;
        if (!ref.isValid()) return;

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        world.execute(() -> {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d position = transform.getPosition();
            int playerX = (int) position.x;
            int playerY = (int) position.y;
            int playerZ = (int) position.z;

            int gridOriginX = playerX - OFFSET_WEST;
            int gridOriginZ = playerZ - OFFSET_NORTH;

            Placer.placePrefab("Labyrinth_Done", world, new Vector3i(playerX, playerY, playerZ));
            LabyrinthState.set(gridOriginX, playerY, gridOriginZ, SECTIONS_X, SECTIONS_Z);

            Store<EntityStore> worldEntityStore = world.getEntityStore().getStore();
            WorldTimeResource timeResource = worldEntityStore.getResource(WorldTimeResource.getResourceType());
            timeResource.setDayTime(0.0, world, worldEntityStore);

            commandContext.sendMessage(Message.raw("Labyrinth spawned at " + playerX + " " + playerY + " " + playerZ));
        });
    }
}
