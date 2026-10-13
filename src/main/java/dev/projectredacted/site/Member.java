package dev.projectredacted.site;

/** A player who works at a site. The Director sets their clearance and what they may do. */
public class Member {
    public String name;
    /** 0-5. Staff obey a player only if this is at least the staff member's clearance. */
    public int clearance = 1;
    public boolean build = true;
    public boolean terminal = false;
    public boolean assign = false;
    /** Optional job the player does, like an NPC role (security, research...). */
    public String job = "Personnel";

    public Member() {}

    public Member(String name) {
        this.name = name;
    }
}
