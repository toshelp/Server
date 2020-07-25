package org.cloudburstmc.server.block.behavior;

import org.cloudburstmc.server.block.BlockState;
import org.cloudburstmc.server.utils.BlockColor;

public abstract class BlockBehaviorTransparent extends BlockBehavior {

    @Override
    public boolean isTransparent() {
        return true;
    }

    @Override
    public BlockColor getColor(BlockState state) {
        return BlockColor.TRANSPARENT_BLOCK_COLOR;
    }

}
