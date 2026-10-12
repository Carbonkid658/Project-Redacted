package dev.projectredacted.staff;

/** A clipboard target: a room number, an SCP's cell, a checkpoint or an entrance (all stored as room numbers). */
public class Assignment {
    public enum Kind { NONE, ROOM, SCP, CHECKPOINT, ENTRANCE, FOLLOW }

    public Kind kind = Kind.NONE;
    public int room;
    /** FOLLOW: the player (security) or researcher (D-Class) to follow. */
    public java.util.UUID leader;

    public static Assignment of(Kind kind, int room) {
        Assignment a = new Assignment();
        a.kind = kind;
        a.room = room;
        return a;
    }
}
