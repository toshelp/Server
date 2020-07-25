package org.cloudburstmc.server.block.behavior;

import com.nukkitx.math.vector.Vector3f;
import org.cloudburstmc.server.block.BlockState;
import org.cloudburstmc.server.item.Item;
import org.cloudburstmc.server.item.ItemTool;
import org.cloudburstmc.server.math.AxisAlignedBB;
import org.cloudburstmc.server.math.Direction;
import org.cloudburstmc.server.math.SimpleAxisAlignedBB;
import org.cloudburstmc.server.player.Player;

public abstract class BlockBehaviorStairs extends BlockBehaviorTransparent {

    @Override
    public float getMinY() {
        // TODO: this seems wrong
        return this.getY() + ((getMeta() & 0x04) > 0 ? 0.5f : 0);
    }

    @Override
    public float getMaxY() {
        // TODO: this seems wrong
        return this.getY() + ((getMeta() & 0x04) > 0 ? 1 : 0.5f);
    }

    @Override
    public boolean place(Item item, Block block, Block target, Direction face, Vector3f clickPos, Player player) {
        int[] faces = new int[]{2, 1, 3, 0};
        this.setMeta(faces[player != null ? player.getDirection().getHorizontalIndex() : 0]);
        if ((clickPos.getY() > 0.5 && face != Direction.UP) || face == Direction.DOWN) {
            this.setMeta(this.getMeta() | 0x04); //Upside-down stairs
        }
        this.getLevel().setBlock(blockState.getPosition(), this, true, true);

        return true;
    }

    @Override
    public Item[] getDrops(BlockState blockState, Item hand) {
        if (hand.isPickaxe() && hand.getTier() >= ItemTool.TIER_WOODEN) {
            return new Item[]{
                    toItem(blockState)
            };
        } else {
            return new Item[0];
        }
    }

    @Override
    public Item toItem(BlockState state) {
        Item item = super.toItem(blockState);
        item.setMeta(0);
        return item;
    }

    @Override
    public boolean collidesWithBB(AxisAlignedBB bb) {
        int damage = this.getMeta();
        int side = damage & 0x03;
        float f = 0;
        float f1 = 0.5f;
        float f2 = 0.5f;
        float f3 = 1;
        if ((damage & 0x04) > 0) {
            f = 0.5f;
            f1 = 1;
            f2 = 0;
            f3 = 0.5f;
        }

        if (bb.intersectsWith(new SimpleAxisAlignedBB(
                this.getX(),
                this.getY() + f,
                this.getZ(),
                this.getX() + 1,
                this.getY() + f1,
                this.getZ() + 1
        ))) {
            return true;
        }


        if (side == 0) {
            if (bb.intersectsWith(new SimpleAxisAlignedBB(
                    this.getX() + 0.5f,
                    this.getY() + f2,
                    this.getZ(),
                    this.getX() + 1,
                    this.getY() + f3,
                    this.getZ() + 1
            ))) {
                return true;
            }
        } else if (side == 1) {
            if (bb.intersectsWith(new SimpleAxisAlignedBB(
                    this.getX(),
                    this.getY() + f2,
                    this.getZ(),
                    this.getX() + 0.5f,
                    this.getY() + f3,
                    this.getZ() + 1
            ))) {
                return true;
            }
        } else if (side == 2) {
            if (bb.intersectsWith(new SimpleAxisAlignedBB(
                    this.getX(),
                    this.getY() + f2,
                    this.getZ() + 0.5f,
                    this.getX() + 1,
                    this.getY() + f3,
                    this.getZ() + 1
            ))) {
                return true;
            }
        } else if (side == 3) {
            if (bb.intersectsWith(new SimpleAxisAlignedBB(
                    this.getX(),
                    this.getY() + f2,
                    this.getZ(),
                    this.getX() + 1,
                    this.getY() + f3,
                    this.getZ() + 0.5f
            ))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Direction getBlockFace() {
        return Direction.fromHorizontalIndex(this.getMeta() & 0x7);
    }

    @Override
    public boolean canWaterlogSource() {
        return true;
    }
}
