package eu.pb4.polymer.core.mixin.item;

import eu.pb4.polymer.common.impl.CommonImplUtils;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.impl.ClientMetadataKeys;
import eu.pb4.polymer.core.impl.PolymerImpl;
import eu.pb4.polymer.core.impl.interfaces.PolymerNetworkHandlerExtension;
import eu.pb4.polymer.core.impl.networking.PolymerServerProtocol;
import eu.pb4.polymer.networking.api.PolymerServerNetworking;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Equipment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.tag.TagPacketSerializer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = ServerPlayNetworkHandler.class, priority = 1200)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;
    private boolean polymerCore$sentFirstLang = false;

    @Shadow
    public abstract void sendPacket(Packet<?> packet);

    @Unique
    private final List<ItemStack> polymerCore$armorItems = new ArrayList<>();
    @Unique
    private String polymerCore$language = "en_us";

    @Inject(method = "onClientSettings", at = @At("TAIL"))
    private void polymerCore$resendLanguage(ClientSettingsC2SPacket packet, CallbackInfo ci) {
        if (CommonImplUtils.isMainPlayer(this.player)) {
            return;
        }

        if (!this.polymerCore$language.equals(packet.language())) {
            Runnable runnable = () -> {
                PolymerServerProtocol.sendSyncPackets(player.networkHandler, true);
                this.sendPacket(new SynchronizeTagsS2CPacket(TagPacketSerializer.serializeTags(this.player.getServerWorld().getServer().getCombinedDynamicRegistries())));
                this.sendPacket(new SynchronizeRecipesS2CPacket(this.player.getServerWorld().getRecipeManager().values()));
                this.player.getRecipeBook().sendInitRecipesPacket(this.player);
            };

            if (this.polymerCore$sentFirstLang) {
                PolymerNetworkHandlerExtension.of(this.player).polymer$delayAction("language_update", 1500, runnable);
            } else {
                runnable.run();
            }
        } else if (!this.polymerCore$sentFirstLang && PolymerServerNetworking.getMetadata(this.player.networkHandler, ClientMetadataKeys.LANGUAGE, NbtString.TYPE) == null) {
            PolymerServerProtocol.sendSyncPackets(player.networkHandler, true);
        }
        this.polymerCore$sentFirstLang = true;
        this.polymerCore$language = packet.language();
    }

    @Inject(method = "onPlayerInteractBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER))
    private void polymer$resendHandOnPlace(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        ItemStack itemStack = this.player.getStackInHand(packet.getHand());

        if (itemStack.getItem() instanceof PolymerItem polymerItem) {
            var data = PolymerItemUtils.getItemSafely(polymerItem, itemStack, this.player);
            if (data.item() instanceof BlockItem || data.item() instanceof BucketItem) {
                this.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.player.playerScreenHandler.syncId, this.player.playerScreenHandler.nextRevision(), packet.getHand() == Hand.MAIN_HAND ? 36 + this.player.getInventory().selectedSlot : 45, itemStack));
            }
        }
    }

    @Inject(method = "onPlayerInteractItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER))
    private void polymer$resendHandOnUse(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        ItemStack itemStack = this.player.getStackInHand(packet.getHand());

        if (itemStack.getItem() instanceof PolymerItem polymerItem) {
            var data = PolymerItemUtils.getItemSafely(polymerItem, itemStack, this.player);
            if (data.item() instanceof Equipment equipment && equipment.getSlotType().isArmorSlot()) {
                this.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.player.playerScreenHandler.syncId, this.player.playerScreenHandler.nextRevision(), packet.getHand() == Hand.MAIN_HAND ? 36 + this.player.getInventory().selectedSlot : 45, itemStack));

                this.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.player.playerScreenHandler.syncId, this.player.playerScreenHandler.nextRevision(), 8 - equipment.getSlotType().getEntitySlotId(), this.player.getEquippedStack(equipment.getSlotType())));
            }
        }
    }


    @Inject(method = "onPlayerInteractBlock", at = @At("TAIL"))
    private void polymer$updateMoreBlocks(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        if (PolymerImpl.RESEND_BLOCKS_AROUND_CLICK) {
            var base = packet.getBlockHitResult().getBlockPos();
            for (Direction direction : Direction.values()) {
                var pos = base.offset(direction);
                var state = player.getServerWorld().getBlockState(pos);
                player.networkHandler.sendPacket(new BlockUpdateS2CPacket(pos, state));

                if (state.hasBlockEntity()) {
                    var be = player.getWorld().getBlockEntity(pos);
                    if (be != null) {
                        player.networkHandler.sendPacket(BlockEntityUpdateS2CPacket.create(be));
                    }
                }
            }
        }

        /*var stack = this.player.getStackInHand(packet.getHand());

        if (stack.getItem() instanceof PolymerItem virtualItem) {
            var data = PolymerItemUtils.getItemSafely(virtualItem, stack, this.player);
            if (data.item() instanceof BlockItem || data.item() instanceof BucketItem) {
                this.onPlayerInteractItem(new PlayerInteractItemC2SPacket(packet.getHand(), 0));
            }
        }*/
    }

    @Inject(method = "onClickSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;updateLastActionTime()V", shift = At.Shift.AFTER))
    private void polymer$storeArmor(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (this.player.currentScreenHandler == this.player.playerScreenHandler) {
            for (ItemStack stack : this.player.getInventory().armor) {
                polymerCore$armorItems.add(stack.copy());
            }
        }
    }

    @Inject(method = "onClickSlot", at = @At("TAIL"))
    private void polymer$updateArmor(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (this.player.currentScreenHandler == this.player.playerScreenHandler && packet.getSlot() != -999) {
            int x = 0;
            for (ItemStack stack : this.player.getInventory().armor) {
                if (stack.getItem() instanceof PolymerItem && !ItemStack.areEqual(this.polymerCore$armorItems.get(x), stack)) {
                    this.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.player.playerScreenHandler.syncId,
                            this.player.playerScreenHandler.nextRevision(),
                            8 - x,
                            stack));

                    if (packet.getSlot() != 8 - x) {
                        this.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(this.player.playerScreenHandler.syncId,
                                this.player.playerScreenHandler.nextRevision(),
                                packet.getSlot(),
                                this.player.playerScreenHandler.getSlot(packet.getSlot()).getStack()));
                    }

                    this.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(-1,
                            0,
                            0,
                            this.player.currentScreenHandler.getCursorStack()));
                    return;
                }
                x++;
            }
        }

        this.polymerCore$armorItems.clear();
    }
}
