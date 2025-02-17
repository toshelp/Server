package org.cloudburstmc.server.block.behavior;

import com.nukkitx.math.vector.Vector3f;
import org.cloudburstmc.api.block.Block;
import org.cloudburstmc.api.block.BlockStates;
import org.cloudburstmc.api.enchantment.EnchantmentTypes;
import org.cloudburstmc.api.entity.Entity;
import org.cloudburstmc.api.entity.EntityTypes;
import org.cloudburstmc.api.entity.misc.PrimedTnt;
import org.cloudburstmc.api.item.ItemStack;
import org.cloudburstmc.api.item.ItemTypes;
import org.cloudburstmc.api.level.Location;
import org.cloudburstmc.api.player.Player;
import org.cloudburstmc.api.util.data.BlockColor;
import org.cloudburstmc.server.level.CloudLevel;
import org.cloudburstmc.server.level.Sound;
import org.cloudburstmc.server.registry.EntityRegistry;

import java.util.concurrent.ThreadLocalRandom;

public class BlockBehaviorTNT extends BlockBehaviorSolid {


    @Override
    public boolean canBeActivated(Block block) {
        return true;
    }


    public void prime(Block block) {
        this.prime(block, 80);
    }

    public void prime(Block block, int fuse) {
        prime(block, fuse, null);
    }

    public void prime(Block block, int fuse, Entity source) {
        block.set(BlockStates.AIR, true);

        float mot = ThreadLocalRandom.current().nextFloat() * (float) Math.PI * 2f;

        PrimedTnt primedTnt = EntityRegistry.get().newEntity(EntityTypes.TNT,
                Location.from(block.getPosition().toFloat().add(0.5, 0, 0.5), block.getLevel()));
        primedTnt.setMotion(Vector3f.from(-Math.sin(mot) * 0.02, 0.2, -Math.cos(mot) * 0.02));
        primedTnt.setFuse(fuse);
        primedTnt.setSource(source);
        primedTnt.spawnToAll();

        ((CloudLevel) block.getLevel()).addSound(block.getPosition(), Sound.RANDOM_FUSE);
    }

    @Override
    public int onUpdate(Block block, int type) {
        if ((type == CloudLevel.BLOCK_UPDATE_NORMAL || type == CloudLevel.BLOCK_UPDATE_REDSTONE) && ((CloudLevel) block.getLevel()).isBlockPowered(block.getPosition())) {
            this.prime(block);
        }

        return 0;
    }

    @Override
    public boolean onActivate(Block block, ItemStack item, Player player) {
        if (item.getType() == ItemTypes.FLINT_AND_STEEL || item.getEnchantment(EnchantmentTypes.FIRE_ASPECT) != null) {
            item.getBehavior().useOn(item, block.getState());
            this.prime(block, 80, player);
            return true;
        } else if (item.getType() == ItemTypes.FIREBALL) {
            if (!player.isCreative()) {
                player.getInventory().getItemInHand().decrementAmount();
            }
            ((CloudLevel) block.getLevel()).addSound(player.getPosition(), Sound.MOB_GHAST_FIREBALL);
            this.prime(block, 80, player);
            return true;
        }
        return false;
    }

    @Override
    public BlockColor getColor(Block block) {
        return BlockColor.TNT_BLOCK_COLOR;
    }
}
