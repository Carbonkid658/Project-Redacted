package dev.projectredacted.site;

import dev.projectredacted.raid.ArmedNpcEntity;
import dev.projectredacted.registry.ModItems;
import dev.projectredacted.staff.StaffEntity;
import dev.projectredacted.staff.StaffRecord;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

/** Deaths: staff die permanently, raiders drop loot and any SCP items they were carrying off. */
public final class SiteEvents {
    private static final Random RANDOM = new Random();

    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level().isClientSide()) return;
            if (source.getEntity() instanceof StaffEntity killer) {
                StaffRecord kr = killer.record();
                if (kr != null) kr.addXp(entity instanceof ArmedNpcEntity ? 8 : 3);
            }
            if (entity instanceof StaffEntity staff) {
                Site site = staff.site();
                StaffRecord r = site == null ? null : site.staff.remove(staff.getUUID());
                if (r == null) return;
                site.replacementsOwed++;
                for (StaffRecord other : site.staff.values()) other.morale = Math.max(0, other.morale - 8);
                String msg = r.fullName() + " has been killed.";
                if (!Cassie.announce(site, msg)) Cassie.broadcast(site, Component.literal(msg).withStyle(ChatFormatting.RED), false);
            } else if (entity instanceof ArmedNpcEntity npc) {
                for (ItemStack stack : npc.carried) drop(npc, stack);
                npc.carried.clear();
                if (npc.faction().raider) drop(npc, new ItemStack(ModItems.COIN, 1 + RANDOM.nextInt(3)));
            }
        });
    }

    private static void drop(net.minecraft.world.entity.Entity at, ItemStack stack) {
        at.level().addFreshEntity(new ItemEntity(at.level(), at.getX(), at.getY() + 0.5, at.getZ(), stack));
    }

    private SiteEvents() {}
}
