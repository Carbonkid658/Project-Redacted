package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Sound events. The .ogg files are placeholders mapped to vanilla sounds in sounds.json until real C.A.S.S.I.E. audio exists. */
public final class ModSounds {
    public static final SoundEvent CASSIE_CHIME = register("cassie.chime");
    public static final SoundEvent CASSIE_ANNOUNCE = register("cassie.announce");
    public static final SoundEvent SITE_ALARM = register("site.alarm");
    public static final SoundEvent WARHEAD_ALARM = register("site.warhead");
    public static final SoundEvent GUN_PISTOL = register("gun.pistol");
    public static final SoundEvent GUN_RIFLE = register("gun.rifle");
    public static final SoundEvent GUN_SHOTGUN = register("gun.shotgun");
    public static final SoundEvent GUN_RELOAD = register("gun.reload");

    private static SoundEvent register(String path) {
        Identifier id = ProjectRedacted.id(path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {}

    private ModSounds() {}
}
