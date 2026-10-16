package dev.projectredacted.api;

import dev.projectredacted.room.Room;
import dev.projectredacted.site.Site;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** What a containment requirement can look at: the site, the cell, and the SCP (entity SCPs only). */
public record ContainmentContext(ServerLevel level, Site site, Room cell, Entity scp) {}
