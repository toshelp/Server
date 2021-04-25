package org.cloudburstmc.server.player.manager;

import com.nukkitx.protocol.bedrock.data.inventory.ContainerSlotType;
import com.nukkitx.protocol.bedrock.data.inventory.ItemStackRequest;
import com.nukkitx.protocol.bedrock.data.inventory.StackRequestSlotInfoData;
import com.nukkitx.protocol.bedrock.data.inventory.stackrequestactions.PlaceStackRequestActionData;
import com.nukkitx.protocol.bedrock.data.inventory.stackrequestactions.StackRequestActionData;
import com.nukkitx.protocol.bedrock.data.inventory.stackrequestactions.TakeStackRequestActionData;
import com.nukkitx.protocol.bedrock.packet.ItemStackResponsePacket;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.api.blockentity.BlockEntity;
import org.cloudburstmc.server.inventory.*;
import org.cloudburstmc.server.inventory.transaction.CraftingTransaction;
import org.cloudburstmc.server.player.CloudPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.nukkitx.protocol.bedrock.data.inventory.stackrequestactions.StackRequestActionType.*;

@Log4j2
@Getter
public class PlayerInventoryManager {
    private CloudPlayer player;
    private CloudPlayerInventory mainInv;
    private PlayerCursorInventory cursor;
    private CloudEnderChestInventory enderChest;
    private CloudCraftingGrid craftingGrid;
    @Setter
    private CraftingTransaction transaction;
    private BlockEntity viewingBlock;

    public PlayerInventoryManager(CloudPlayer player) {
        this.player = player;
        this.mainInv = new CloudPlayerInventory(player);
        this.cursor = new PlayerCursorInventory(player);
        this.enderChest = new CloudEnderChestInventory(player);
        this.craftingGrid = new CloudCraftingGrid(player);
        transaction = null;
        viewingBlock = null;
    }


    public ItemStackResponsePacket.Response handle(ItemStackRequest request) {
        ItemStackResponsePacket.ResponseStatus result = ItemStackResponsePacket.ResponseStatus.OK;

        List<ItemStackResponsePacket.ContainerEntry> containers = new ArrayList<>();

        if (isCraftingRequest(request)) {
            if (this.transaction == null) {
                log.warn("Received crafting item stack request when crafting transaction is null!");
                return new ItemStackResponsePacket.Response(ItemStackResponsePacket.ResponseStatus.ERROR, request.getRequestId(), containers);
            } else {

                // TODO - create crafing transaction with CraftEventPacket and Execute the crafting request here

                return new ItemStackResponsePacket.Response(ItemStackResponsePacket.ResponseStatus.OK, request.getRequestId(), containers);
            }
        }

        for (StackRequestActionData data : request.getActions()) {
            StackRequestSlotInfoData source;
            StackRequestSlotInfoData target;

            BaseInventory sourceInv;
            BaseInventory targetInv;

            switch (data.getType()) {
                case TAKE:
                case PLACE:
                    if (data.getType() == TAKE) {
                        source = ((TakeStackRequestActionData) data).getSource();
                        target = ((TakeStackRequestActionData) data).getDestination();
                    } else {
                        source = ((PlaceStackRequestActionData) data).getSource();
                        target = ((PlaceStackRequestActionData) data).getDestination();
                    }
                    sourceInv = getInventoryByType(source.getContainer());
                    targetInv = getInventoryByType(target.getContainer());

                    containers.addAll(sourceInv.getContainerEntries());
                    containers.addAll(targetInv.getContainerEntries());

                    //Check Item
                    if (sourceInv.getItem(source.getSlot()).getNetworkData().getNetId() != source.getStackNetworkId()) {
                        result = ItemStackResponsePacket.ResponseStatus.ERROR;
                        break;
                    }

                    if (!targetInv.setItem(target.getSlot(), sourceInv.getItem(source.getSlot()), false)
                            || !sourceInv.clear(source.getSlot(), false)) {
                        result = ItemStackResponsePacket.ResponseStatus.ERROR;
                        break;
                    }
                    result = ItemStackResponsePacket.ResponseStatus.OK;
                    break;
                case DROP:
                    break;
                case SWAP:
                    break;
                case DESTROY:
                case CONSUME:
                    break;
                case CREATE:
                    break;
                case BEACON_PAYMENT:
                    break;
                case MINE_BLOCK:
                    break;

            }
        }
        return new ItemStackResponsePacket.Response(result, request.getRequestId(), containers);
    }

    private boolean isCraftingRequest(ItemStackRequest request) {
        return Arrays.stream(request.getActions()).anyMatch(req -> req.getType() == CRAFT_CREATIVE
                || req.getType() == CRAFT_RECIPE
                || req.getType() == CRAFT_RECIPE_AUTO
                || req.getType() == CRAFT_RECIPE_OPTIONAL
                || req.getType() == CRAFT_RESULTS_DEPRECATED
                || req.getType() == CRAFT_NON_IMPLEMENTED_DEPRECATED
                || req.getType() == CRAFT_RESULTS_DEPRECATED
        );
    }

    private BaseInventory getInventoryByType(ContainerSlotType type) {
        switch (type) {
            case HOTBAR:
            case HOTBAR_AND_INVENTORY:
            case INVENTORY:
            case OFFHAND:
                return mainInv;
            case CRAFTING_INPUT:
            case CRAFTING_OUTPUT:
                return craftingGrid;
            case CURSOR:
                return cursor;
            default:
                return null;
        }


    }
}
