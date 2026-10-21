package com.xy2407.nudge;

import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import com.xy2407.nudge.block.BlockCacheBridge;
import com.xy2407.nudge.block.BlockSectionCache;
import com.xy2407.nudge.duck.NudgeSectionCacheHolder;

/**
 * Installs the version specific section cache resolver for 1.21.1
 */
public final class BlockCacheSetup {

    private BlockCacheSetup() {
    }

    public static void install() {
        BlockCacheBridge.install((blockGetter, pos) -> {
            if (!(blockGetter instanceof LevelChunk chunk)) {
                return null;
            }
            Level level = chunk.getLevel();
            if (level.isClientSide()) {
                return null;
            }
            LevelChunkSection[] sections = chunk.getSections();
            int section = SectionPos.blockToSectionCoord(pos.getY()) - chunk.getMinSection();
            if (section < 0 || section >= sections.length) {
                return null;
            }
            NudgeSectionCacheHolder holder = (NudgeSectionCacheHolder) sections[section];
            BlockSectionCache cache = holder.nudge$getBlockCache();
            if (cache == null) {
                MinecraftServer server = level.getServer();
                if (server == null || !server.isSameThread()) {
                    return null;
                }
                LevelChunkSection target = sections[section];
                cache = new BlockSectionCache();
                cache.scan(target::getBlockState);
                holder.nudge$setBlockCache(cache);
            }
            if (cache.isEmpty()) {
                return Blocks.AIR.defaultBlockState();
            }
            return cache.isOccupied(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15)
                    ? null : Blocks.AIR.defaultBlockState();
        });
    }
}
