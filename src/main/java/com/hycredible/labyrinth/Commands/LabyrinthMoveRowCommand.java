package com.hycredible.labyrinth.Commands;

import com.hycredible.labyrinth.LabyrinthState;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;

public class LabyrinthMoveRowCommand extends CommandBase {

    static final int SECTION_SIZE = 6;
    static final int CLEAR_SETTINGS =
        SetBlockSettings.NO_DROP_ITEMS | SetBlockSettings.NO_SEND_PARTICLES | SetBlockSettings.NO_SEND_AUDIO;

    private static final int PASSENGER_Y_EXTRA = 3;

    // Block offset from the LabyrinthState grid origin to where the labyrinth grid starts.
    private static final int OFFSET_X = 48;
    private static final int OFFSET_Z = 30;

    // Which section to move (0-based section index within the grid).
    private static final int SECTION_X = 0;
    private static final int SECTION_Z = 0;

    // Movement
    private static final String AXIS = "Z";
    private static final int CASCADE_COUNT = 6; // number of ADDITIONAL sections to push in the movement direction
    private static final int DISTANCE = 6;
    private static final int DIRECTION = -1; // positive/negative direction
    private static final float SPEED = 10f;

    // Snapshot of the last committed move, used by LabyrinthUndoRowCommand.
    static MoveSnapshot lastSnapshot;

    static class MoveSnapshot {
        final BlockSelection sourceSelection;
        final BlockSelection destSelection;
        final int startX, startY, startZ;
        final int deltaX, deltaZ;
        final int slotCount;
        final boolean moveAlongX;

        MoveSnapshot(BlockSelection sourceSelection,
                     BlockSelection destSelection,
                     int startX, int startY, int startZ,
                     int deltaX, int deltaZ,
                     int slotCount, boolean moveAlongX) {
            this.sourceSelection = sourceSelection;
            this.destSelection = destSelection;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.deltaX = deltaX;
            this.deltaZ = deltaZ;
            this.slotCount = slotCount;
            this.moveAlongX = moveAlongX;
        }
    }

    public LabyrinthMoveRowCommand() {
        super("labyrinth:moverow", "Move a row of labyrinth sections");
        this.setPermissionGroups(String.valueOf(GameMode.Adventure));
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        if (!LabyrinthState.isSet()) {
            ctx.sendMessage(Message.raw("No labyrinth has been spawned yet."));
            return;
        }

        Ref<EntityStore> ref = ctx.senderAsPlayerRef();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("This command must be run by a player."));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        boolean moveAlongX = AXIS.equals("X");
        int deltaX = moveAlongX ? DISTANCE * DIRECTION : 0;
        int deltaZ = moveAlongX ? 0 : DISTANCE * DIRECTION;
        int totalSlots = 1 + CASCADE_COUNT;

        // When moving in the negative direction, the cascade sections are behind the primary
        // in world space, so shift the effective start back by CASCADE_COUNT sections along the axis.
        int startX = LabyrinthState.getOriginX() + OFFSET_X + SECTION_X * SECTION_SIZE
            + (moveAlongX ? Math.min(deltaX * CASCADE_COUNT, 0) : 0);
        int startY = LabyrinthState.getOriginY();
        int startZ = LabyrinthState.getOriginZ() + OFFSET_Z + SECTION_Z * SECTION_SIZE
            + (moveAlongX ? 0 : Math.min(deltaZ * CASCADE_COUNT, 0));

        world.execute(() -> {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();

            // Phase 1: read source and destination blocks into memory before anything changes
            int slotVolume = totalSlots * SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
            BlockSelection selection     = new BlockSelection(slotVolume, 0);
            BlockSelection destSelection = new BlockSelection(slotVolume, 0);

            for (int slot = 0; slot < totalSlots; slot++) {
                int slotOffsetX = moveAlongX ? slot * SECTION_SIZE : 0;
                int slotOffsetZ = moveAlongX ? 0 : slot * SECTION_SIZE;
                for (int dx = 0; dx < SECTION_SIZE; dx++) {
                    for (int dy = 0; dy < SECTION_SIZE; dy++) {
                        for (int dz = 0; dz < SECTION_SIZE; dz++) {
                            int srcX = startX + slotOffsetX + dx;
                            int wy   = startY + dy;
                            int srcZ = startZ + slotOffsetZ + dz;
                            int dstX = srcX + deltaX;
                            int dstZ = srcZ + deltaZ;

                            WorldChunk srcChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(srcX, srcZ));
                            if (srcChunk != null) selection.copyFromAtWorld(srcX, wy, srcZ, srcChunk, null);

                            WorldChunk dstChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(dstX, dstZ));
                            if (dstChunk != null) destSelection.copyFromAtWorld(dstX, wy, dstZ, dstChunk, null);
                        }
                    }
                }
            }

            // Collect passengers before clearing.
            int totalWidthX = moveAlongX ? totalSlots * SECTION_SIZE : SECTION_SIZE;
            int totalDepthZ = moveAlongX ? SECTION_SIZE : totalSlots * SECTION_SIZE;
            Vector3d aabbMin = new Vector3d(startX, startY, startZ);
            Vector3d aabbMax = new Vector3d(
                startX + totalWidthX,
                startY + SECTION_SIZE + PASSENGER_Y_EXTRA,
                startZ + totalDepthZ
            );

            List<Ref<EntityStore>> tempRefs = SpatialResource.getThreadLocalReferenceList();
            entityStore.getResource(EntityModule.get().getEntitySpatialResourceType())
                .getSpatialStructure().collectBox(aabbMin, aabbMax, tempRefs);

            List<Ref<EntityStore>> passengers = new ArrayList<>(tempRefs.size());
            List<Vector3d> passengerStarts = new ArrayList<>(tempRefs.size());
            for (Ref<EntityStore> passengerRef : tempRefs) {
                if (!passengerRef.isValid()) continue;
                TransformComponent tc = entityStore.getComponent(passengerRef, TransformComponent.getComponentType());
                if (tc == null) continue;
                passengers.add(passengerRef);
                passengerStarts.add(new Vector3d(tc.getPosition()));
            }

            // Phase 2: clear source region
            for (int slot = 0; slot < totalSlots; slot++) {
                int slotOffsetX = moveAlongX ? slot * SECTION_SIZE : 0;
                int slotOffsetZ = moveAlongX ? 0 : slot * SECTION_SIZE;
                for (int dx = 0; dx < SECTION_SIZE; dx++) {
                    for (int dy = 0; dy < SECTION_SIZE; dy++) {
                        for (int dz = 0; dz < SECTION_SIZE; dz++) {
                            int wx = startX + slotOffsetX + dx;
                            int wy = startY + dy;
                            int wz = startZ + slotOffsetZ + dz;
                            WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(wx, wz));
                            if (chunk == null) continue;
                            chunk.setBlock(wx, wy, wz, 0, BlockType.EMPTY, 0, 0, CLEAR_SETTINGS);
                        }
                    }
                }
            }

            // Phase 3: place or animate
            MoveSnapshot snapshot = new MoveSnapshot(selection, destSelection, startX, startY, startZ, deltaX, deltaZ, totalSlots, moveAlongX);

            if (SPEED <= 0f) {
                for (int i = 0; i < passengers.size(); i++) {
                    Ref<EntityStore> passengerRef = passengers.get(i);
                    if (!passengerRef.isValid()) continue;
                    Vector3d s = passengerStarts.get(i);
                    TransformComponent tc = entityStore.getComponent(passengerRef, TransformComponent.getComponentType());
                    if (tc == null) continue;
                    tc.setPosition(new Vector3d(s.x + deltaX, s.y, s.z + deltaZ));
                }
                selection.placeNoReturn(world, new Vector3i(deltaX, 0, deltaZ), entityStore);
                lastSnapshot = snapshot;
            } else {
                animateMove(world, entityStore, selection, deltaX, deltaZ, SPEED, passengers, passengerStarts, snapshot);
            }
        });
    }

    private static void animateMove(
        World world,
        Store<EntityStore> entityStore,
        BlockSelection selection,
        int deltaX,
        int deltaZ,
        float speed,
        List<Ref<EntityStore>> passengers,
        List<Vector3d> passengerStarts,
        MoveSnapshot snapshot
    ) {
        TimeResource timeResource = entityStore.getResource(TimeResource.getResourceType());

        List<Ref<EntityStore>> blockEntityRefs = new ArrayList<>();
        List<Vector3d> blockEntityStarts = new ArrayList<>();

        selection.forEachBlock((lx, ly, lz, blockHolder) -> {
            int blockId = blockHolder.blockId();
            if (blockId == 0) return;
            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
            if (blockType == null || blockType.getId() == null) return;

            Vector3d startPos = new Vector3d(lx + 0.5, ly + 0.5, lz + 0.5);
            Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(timeResource, blockType.getId(), startPos);
            holder.removeComponent(DespawnComponent.getComponentType());

            Ref<EntityStore> entityRef = entityStore.addEntity(holder, AddReason.SPAWN);
            blockEntityRefs.add(entityRef);
            blockEntityStarts.add(startPos);
        });

        if (blockEntityRefs.isEmpty() && passengers.isEmpty()) {
            selection.placeNoReturn(world, new Vector3i(deltaX, 0, deltaZ), entityStore);
            lastSnapshot = snapshot;
            return;
        }

        float distance = (float) Math.sqrt((double) deltaX * deltaX + (double) deltaZ * deltaZ);
        long durationMs = Math.max(1L, (long) (distance / speed * 1000f));
        long startMs = System.currentTimeMillis();

        Runnable[] tickHolder = {null};
        tickHolder[0] = () -> {
            long elapsed = System.currentTimeMillis() - startMs;
            float t = Math.min(1.0f, (float) elapsed / durationMs);

            for (int i = 0; i < blockEntityRefs.size(); i++) {
                Ref<EntityStore> entityRef = blockEntityRefs.get(i);
                if (!entityRef.isValid()) continue;
                Vector3d s = blockEntityStarts.get(i);
                TransformComponent tc = entityStore.getComponent(entityRef, TransformComponent.getComponentType());
                if (tc == null) continue;
                tc.setPosition(new Vector3d(s.x + t * deltaX, s.y, s.z + t * deltaZ));
            }

            for (int i = 0; i < passengers.size(); i++) {
                Ref<EntityStore> passengerRef = passengers.get(i);
                if (!passengerRef.isValid()) continue;
                Vector3d s = passengerStarts.get(i);
                TransformComponent tc = entityStore.getComponent(passengerRef, TransformComponent.getComponentType());
                if (tc == null) continue;
                tc.setPosition(new Vector3d(s.x + t * deltaX, s.y, s.z + t * deltaZ));
            }

            if (t < 1.0f) {
                world.execute(tickHolder[0]);
            } else {
                for (Ref<EntityStore> entityRef : blockEntityRefs) {
                    if (entityRef.isValid()) {
                        entityStore.removeEntity(entityRef, RemoveReason.REMOVE);
                    }
                }
                selection.placeNoReturn(world, new Vector3i(deltaX, 0, deltaZ), entityStore);
                lastSnapshot = snapshot;
            }
        };
        world.execute(tickHolder[0]);
    }
}
