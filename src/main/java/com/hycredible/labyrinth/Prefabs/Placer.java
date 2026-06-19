package com.hycredible.labyrinth.Prefabs;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

public class Placer {
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private String prefabPath;

    public static void placePrefab(String prefabName, World world, Vector3i position) {
        BlockSelection prefab = findPrefab(prefabName);
        if (prefab == null) {
            LOGGER.atWarning().log("Prefab not found: " + prefabName);
            return;
        }
        prefab.placeNoReturn(world, position, world.getEntityStore().getStore());
    }

    /**
     * Helpers
     */
    private static BlockSelection findPrefab(String prefabName) {
        String filename = prefabName + ".prefab.json";
        BlockSelection prefab = PrefabStore.get().getAssetPrefabFromAnyPack(filename);
        if (prefab != null) return prefab;
        return null;
    }
}
