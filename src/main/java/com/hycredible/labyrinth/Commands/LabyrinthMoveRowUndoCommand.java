package com.hycredible.labyrinth.Commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

public class LabyrinthMoveRowUndoCommand extends CommandBase {

    public LabyrinthMoveRowUndoCommand() {
        super("labyrinth:moverow_undo", "Undo the last labyrinth:moverow");
        this.setPermissionGroups(String.valueOf(GameMode.Adventure));
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        LabyrinthMoveRowCommand.MoveSnapshot snapshot = LabyrinthMoveRowCommand.lastSnapshot;
        if (snapshot == null) {
            ctx.sendMessage(Message.raw("Nothing to undo."));
            return;
        }

        Ref<EntityStore> ref = ctx.senderAsPlayerRef();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("This command must be run by a player."));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        world.execute(() -> {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();

            // Clear the destination region (where the blocks were moved to).
            for (int slot = 0; slot < snapshot.slotCount; slot++) {
                int slotOffsetX = snapshot.moveAlongX ? slot * LabyrinthMoveRowCommand.SECTION_SIZE : 0;
                int slotOffsetZ = snapshot.moveAlongX ? 0 : slot * LabyrinthMoveRowCommand.SECTION_SIZE;
                for (int dx = 0; dx < LabyrinthMoveRowCommand.SECTION_SIZE; dx++) {
                    for (int dy = 0; dy < LabyrinthMoveRowCommand.SECTION_SIZE; dy++) {
                        for (int dz = 0; dz < LabyrinthMoveRowCommand.SECTION_SIZE; dz++) {
                            int wx = snapshot.startX + snapshot.deltaX + slotOffsetX + dx;
                            int wy = snapshot.startY + dy;
                            int wz = snapshot.startZ + snapshot.deltaZ + slotOffsetZ + dz;
                            WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(wx, wz));
                            if (chunk == null) continue;
                            chunk.setBlock(wx, wy, wz, 0, BlockType.EMPTY, 0, 0, LabyrinthMoveRowCommand.CLEAR_SETTINGS);
                        }
                    }
                }
            }

            // Restore A to its original position and B to the destination (both stored at absolute world coords).
            snapshot.sourceSelection.placeNoReturn(world, new Vector3i(0, 0, 0), entityStore);
            snapshot.destSelection.placeNoReturn(world, new Vector3i(0, 0, 0), entityStore);

            LabyrinthMoveRowCommand.lastSnapshot = null;
        });
    }
}
