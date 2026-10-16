package dev.projectredacted.api;

/** SCP object classes. Weight feeds raid chance (held SCPs weighted by class and count). */
public enum ObjectClass {
    SAFE(1), EUCLID(2), KETER(3), THAUMIEL(1), NEUTRALIZED(0), UNCLASSIFIED(1);

    public final int raidWeight;

    ObjectClass(int raidWeight) {
        this.raidWeight = raidWeight;
    }

    /** Which standard cell type the Serpent's Hand hack opens at each stage: Safe at 2, Keter at 3, everything at 4. */
    public int handHackStage() {
        return switch (this) {
            case SAFE, NEUTRALIZED, UNCLASSIFIED -> 2;
            case KETER -> 3;
            default -> 4;
        };
    }
}
