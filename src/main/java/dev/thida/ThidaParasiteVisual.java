package dev.thida;

import net.minecraft.core.Rotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ThidaParasiteVisual {
    public static void style(ArmorStand doll) {
        CompoundTag shape = new CompoundTag();
        shape.putBoolean("Small", true); shape.putBoolean("Marker", true);
        shape.putBoolean("ShowArms", true); shape.putBoolean("NoBasePlate", true);
        doll.readAdditionalSaveData(shape);
        doll.setNoGravity(true); doll.setInvulnerable(true);
        doll.setCustomName(null); doll.setCustomNameVisible(false);
        doll.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ThidaMod.PARASITE_MASK.get()));
        ItemStack robe = new ItemStack(Items.LEATHER_CHESTPLATE);
        ((DyeableLeatherItem)robe.getItem()).setColor(robe, 0x29152F);
        doll.setItemSlot(EquipmentSlot.CHEST, robe);
        ItemStack legs = new ItemStack(Items.LEATHER_LEGGINGS);
        ((DyeableLeatherItem)legs.getItem()).setColor(legs, 0x573047);
        doll.setItemSlot(EquipmentSlot.LEGS, legs);
        doll.setLeftArmPose(new Rotations(-32, -12, -20));
        doll.setRightArmPose(new Rotations(-32, 12, 20));
        doll.setLeftLegPose(new Rotations(8, 0, -6));
        doll.setRightLegPose(new Rotations(-8, 0, 6));
        doll.getPersistentData().putInt("thidaParasiteStyle", 2);
    }
    public static void follow(ArmorStand doll, ServerPlayer owner) {
        if (doll.getPersistentData().getInt("thidaParasiteStyle") != 2) style(doll);
        double angle = Math.toRadians(owner.getYRot());
        doll.setPos(owner.getX() + Math.cos(angle)*.85 + Math.sin(angle)*.25,
                owner.getY() + .6 + Math.sin(owner.tickCount*.09)*.06,
                owner.getZ() + Math.sin(angle)*.85 - Math.cos(angle)*.25);
        doll.setYRot(owner.getYRot());
        doll.setYBodyRot(owner.getYRot()); doll.setYHeadRot(owner.getYRot());
        doll.setHeadPose(new Rotations(Math.max(-30, Math.min(30, owner.getXRot())), 0, 0));
    }
    private ThidaParasiteVisual() {}
}
