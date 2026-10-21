package com.xy2407.collision.duck;

import com.xy2407.collision.block.BlockSectionCache;

/**
 * Exposes the per-section block cache stored on every level chunk section
 */
public interface NudgeSectionCacheHolder {

    BlockSectionCache nudge$getBlockCache();

    void nudge$setBlockCache(BlockSectionCache cache);
}
