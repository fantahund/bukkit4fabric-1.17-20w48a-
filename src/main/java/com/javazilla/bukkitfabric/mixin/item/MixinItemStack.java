package com.javazilla.bukkitfabric.mixin.item;

import java.util.Random;
import java.util.function.Consumer;

import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.javazilla.bukkitfabric.impl.BukkitEventFactory;
import com.javazilla.bukkitfabric.interfaces.IMixinServerEntityPlayer;
import com.javazilla.bukkitfabric.interfaces.IMixinWorld;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Blocks;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.UnbreakingEnchantment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockPlaceEvent;

@Mixin(value = ItemStack.class, priority = 999)
public class MixinItemStack {

    @Shadow
    private Item item;

    @Inject(at = @At("HEAD"), method = "damage", cancellable = true)
    public void callPlayerItemDamageEvent(int i, Random random, ServerPlayerEntity entityplayer, CallbackInfoReturnable<Boolean> ci) {
        if (!((ItemStack)(Object)this).isDamageable()) {
            ci.setReturnValue(false);
            return;
        }
        int j;

        if (i > 0) {
            j = EnchantmentHelper.getLevel(Enchantments.UNBREAKING, ((ItemStack)(Object)this));
            for (int l = 0; j > 0 && l < i; ++l) if (UnbreakingEnchantment.shouldPreventDamage(((ItemStack)(Object)this), j, random)) i--;

            if (entityplayer != null) {
                PlayerItemDamageEvent event = new PlayerItemDamageEvent((Player) ((IMixinServerEntityPlayer)entityplayer).getBukkitEntity(), CraftItemStack.asCraftMirror((ItemStack)(Object)this), i);
                event.getPlayer().getServer().getPluginManager().callEvent(event);

                if (i != event.getDamage() || event.isCancelled()) event.getPlayer().updateInventory();
                if (event.isCancelled()) {
                    ci.setReturnValue(false);
                    return;
                }
                i = event.getDamage();
            }
            if (i <= 0) {
                ci.setReturnValue(false);
                return;
            }
        }
        if (entityplayer != null && i != 0) Criteria.ITEM_DURABILITY_CHANGED.trigger(entityplayer, ((ItemStack)(Object)this), ((ItemStack)(Object)this).getDamage() + i);

        ((ItemStack)(Object)this).setDamage((j = ((ItemStack)(Object)this).getDamage() + i));
        ci.setReturnValue(j >= ((ItemStack)(Object)this).getMaxDamage());
        return;
    }

    /**
     * @author
     */
    @Overwrite
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity playerEntity = context.getPlayer();
        BlockPos blockPos = context.getBlockPos();
        CachedBlockPosition cachedBlockPosition = new CachedBlockPosition(context.getWorld(), blockPos, false);
        if (playerEntity != null && !playerEntity.getAbilities().allowModifyWorld && !((ItemStack)(Object)this).canPlaceOn(context.getWorld().getTagManager(), cachedBlockPosition)) {
            return ActionResult.PASS;
        }
        ((IMixinWorld)context.getWorld()).setCaptureBlockStates_BF(true);

        Item item = ((ItemStack)(Object)this).getItem();
        ActionResult actionResult = item.useOnBlock(context);

        if (actionResult != ActionResult.FAIL) {
            List<BlockState> blocks = new java.util.ArrayList<>(((IMixinWorld)context.getWorld()).getCapturedBlockStates_BF().values());
            ((IMixinWorld)context.getWorld()).getCapturedBlockStates_BF().clear();
            BlockPlaceEvent placeEvent = BukkitEventFactory.callBlockPlaceEvent((ServerWorld)context.getWorld(), playerEntity, Hand.MAIN_HAND, blocks.get(0), blockPos.getX(), blockPos.getY(), blockPos.getZ());
            placeEvent.setCancelled(true); // test
            if ((placeEvent.isCancelled() || !placeEvent.canBuild())) {
                ((IMixinWorld)context.getWorld()).setCaptureBlockStates_BF(false);
                BlockPos pos = context.getBlockPos().add(0, 1, 0);
                while (context.getWorld().getBlockState(pos) != Blocks.AIR.getDefaultState()) {
                    context.getWorld().setBlockState(context.getBlockPos().add(0, 1, 0), Blocks.AIR.getDefaultState());
                }
                context.getStack().increment(1);
                ((Player)((IMixinServerEntityPlayer)context.getPlayer()).getBukkitEntity()).updateInventory();
                return ActionResult.FAIL;
            }
        }

        if (playerEntity != null && actionResult.isAccepted()) {
            playerEntity.incrementStat(Stats.USED.getOrCreateStat(item));
        }
        ((IMixinWorld)context.getWorld()).setCaptureBlockStates_BF(false);
        return actionResult;
    }

    @Inject(at = @At("HEAD"), method = "damage(ILnet/minecraft/entity/LivingEntity;Ljava/util/function/Consumer;)V", cancellable = true)
    public <T extends LivingEntity> void damage(int i, T t0, Consumer<T> consumer, CallbackInfo ci) {
        if (!t0.world.isClient && (!(t0 instanceof PlayerEntity) || !((PlayerEntity) t0).getAbilities().creativeMode)) {
            if (((ItemStack)(Object)this).isDamageable()) {
                if (((ItemStack)(Object)this).damage(i, t0.getRandom(), t0 instanceof ServerPlayerEntity ? (ServerPlayerEntity) t0 : null)) {
                    consumer.accept(t0);
                    Item item = ((ItemStack)(Object)this).getItem();
                    if (((ItemStack)(Object)this).count == 1 && t0 instanceof PlayerEntity)
                        BukkitEventFactory.callPlayerItemBreakEvent((PlayerEntity) t0, ((ItemStack)(Object)this));

                    ((ItemStack)(Object)this).decrement(1);
                    if (t0 instanceof PlayerEntity)
                        ((PlayerEntity) t0).incrementStat(Stats.BROKEN.getOrCreateStat(item));
                    ((ItemStack)(Object)this).setDamage(0);
                }

            }
        }
        ci.cancel();
        return;
    }

}