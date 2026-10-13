package dev.projectredacted.item;

import dev.projectredacted.gui.Gui;
import dev.projectredacted.gui.screen.OneOffScreens;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Research Documents from labs and observation rooms. Reading one opens the Document Reader. */
public class ResearchDocumentItem extends Item {
    private static final String BODY = "Observation logs, test results and containment notes compiled by your researchers. "
            + "Most of it is redacted for clearance reasons; the rest is coffee stains.\n\n"
            + "Carry these to the Mainframe Terminal: each one used on a Research upgrade cuts its time by 20%, up to four at once. "
            + "The Containment Protocols upgrade is paid for in documents.";

    public ResearchDocumentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) Gui.open((ServerPlayer) player, new OneOffScreens.Document("Research Document", BODY));
        return InteractionResult.SUCCESS;
    }
}
