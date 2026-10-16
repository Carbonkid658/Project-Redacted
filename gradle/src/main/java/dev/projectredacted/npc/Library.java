package dev.projectredacted.npc;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.registry.ModEntities;
import dev.projectredacted.site.SiteManager;
import dev.projectredacted.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** The Wanderers' Library, home of the Serpent's Hand. Reached through Ways that appear in the Overworld. */
public final class Library {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ProjectRedacted.id("library"));
    private static final BlockPos ARRIVAL = new BlockPos(0, 65, 0);

    public static void enter(ServerPlayer player) {
        ServerLevel library = player.level().getServer().getLevel(DIMENSION);
        if (library == null) {
            Msg.send(player, "The Way leads nowhere. (The Library dimension is missing.)", ChatFormatting.GRAY);
            return;
        }
        SiteManager.libraryReturns().put(player.getUUID(), player.blockPosition());
        if (!SiteManager.libraryBuilt()) {
            build(library);
            SiteManager.markLibraryBuilt();
        }
        player.teleport(new TeleportTransition(library, Vec3.atBottomCenterOf(ARRIVAL), Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
        Msg.send(player, "You step through the Way into the Wanderers' Library.", ChatFormatting.DARK_PURPLE);
    }

    public static void leave(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        BlockPos fallback = new BlockPos(0, overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
        BlockPos back = SiteManager.libraryReturns().getOrDefault(player.getUUID(), fallback);
        player.teleport(new TeleportTransition(overworld, Vec3.atBottomCenterOf(back), Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
    }

    /** A reading hall of bookshelves around the arrival point, with the Librarian at the desk. */
    private static void build(ServerLevel level) {
        int r = 12;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                level.setBlock(new BlockPos(x, 64, z), (x + z) % 2 == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, 72, z), Blocks.DARK_OAK_PLANKS.defaultBlockState(), 2);
                boolean edge = Math.abs(x) == r || Math.abs(z) == r;
                boolean shelfRow = !edge && Math.abs(x) > 3 && x % 3 == 0 && Math.abs(z) < r - 2;
                for (int y = 65; y < 72; y++) {
                    if (edge || shelfRow && y < 70) level.setBlock(new BlockPos(x, y, z), Blocks.BOOKSHELF.defaultBlockState(), 2);
                }
                if (!edge && x % 4 == 0 && z % 4 == 0) level.setBlock(new BlockPos(x, 71, z), Blocks.LANTERN.defaultBlockState(), 2);
            }
        }
        level.setBlock(new BlockPos(0, 65, 3), Blocks.LECTERN.defaultBlockState(), 2);
        TraderEntity librarian = ModEntities.TRADER.create(level, EntitySpawnReason.STRUCTURE);
        if (librarian != null) {
            librarian.setKind(TraderKind.LIBRARIAN);
            librarian.snapTo(0.5, 65, 4.5, 180, 0);
            level.addFreshEntity(librarian);
        }
    }

    private Library() {}
}
