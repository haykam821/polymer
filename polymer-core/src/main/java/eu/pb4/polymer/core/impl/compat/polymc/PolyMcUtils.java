package eu.pb4.polymer.core.impl.compat.polymc;

import eu.pb4.polymer.common.impl.CompatStatus;
import io.github.theepicblock.polymc.PolyMc;
import io.github.theepicblock.polymc.api.item.ItemLocation;
import io.github.theepicblock.polymc.api.misc.PolyMapProvider;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;

public class PolyMcUtils {

    public static BlockState toVanilla(BlockState state, ServerPlayerEntity player) {
        if (CompatStatus.POLYMC) {
            return PolyMapProvider.getPolyMap(player).getClientState(state, player);
        }

        return state;
    }

    public static ItemStack toVanilla(ItemStack stack, ServerPlayerEntity player) {
        if (CompatStatus.POLYMC && !stack.isEmpty()) {
            var out = PolyMapProvider.getPolyMap(player).getClientItem(stack, player, ItemLocation.INVENTORY);
            if (!ItemStack.canCombine(stack, out)) {
                out = out.copy();
                out.setSubNbt("PolyMcOriginal", stack.writeNbt(new NbtCompound()));
                return out;
            }
        }

        return stack;
    }

    public static boolean isServerSide(Registry reg, Object obj) {
        return !reg.getId(obj).getNamespace().equals("minecraft");
    }
}
