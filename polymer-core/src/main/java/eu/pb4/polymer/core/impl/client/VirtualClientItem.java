package eu.pb4.polymer.core.impl.client;

import eu.pb4.polymer.common.impl.CommonImplUtils;
import eu.pb4.polymer.core.api.client.ClientPolymerItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;
import net.minecraft.world.World;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
@ApiStatus.Experimental
@Environment(EnvType.CLIENT)
public class VirtualClientItem extends Item {
    private ClientPolymerItem polymerItem;

    public static VirtualClientItem of(ClientPolymerItem item) {
        var obj = CommonImplUtils.createUnsafe(VirtualClientItem.class);
        obj.polymerItem = item;
        return obj;
    }

    @Override
    public RegistryEntry.Reference<Item> getRegistryEntry() {
        return this.polymerItem.visualStack().getItem().getRegistryEntry();
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return Rarity.COMMON;
    }

    @Override
    public Text getName() {
        return this.polymerItem.visualStack().getName();
    }

    @Override
    public Text getName(ItemStack stack) {
        return this.polymerItem.visualStack().getName();
    }

    public ClientPolymerItem getPolymerEntry() {
        return this.polymerItem;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (this.polymerItem.visualStack().hasNbt()) {
            for (var text : this.polymerItem.visualStack().getNbt().getCompound(ItemStack.DISPLAY_KEY).getList(ItemStack.LORE_KEY, NbtElement.STRING_TYPE)) {
                tooltip.add(Text.Serializer.fromLenientJson(text.asString()));
            }
        }
    }

    @Override
    public FeatureSet getRequiredFeatures() {
        return FeatureSet.empty();
    }

    private VirtualClientItem() {
        super(null);
    }
}
