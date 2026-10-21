package com.xy2407.nudge.block;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads section local block states without binding to version specific chunk classes
 */
public interface SectionStateReader {

    BlockState get(int x, int y, int z);
}
