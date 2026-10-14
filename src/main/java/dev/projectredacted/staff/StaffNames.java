package dev.projectredacted.staff;

import java.util.Random;

public final class StaffNames {
    private static final String[] FIRST = {"Alex", "Sam", "Jordan", "Morgan", "Casey", "Riley", "Taylor", "Jamie", "Avery", "Quinn",
            "Harper", "Rowan", "Elliot", "Dana", "Robin", "Kai", "Sasha", "Noel", "Blair", "Emerson", "Reese", "Skyler", "Charlie", "Frankie"};
    private static final String[] LAST = {"Reyes", "Kowalski", "Okafor", "Lindqvist", "Tanaka", "Moreau", "Hughes", "Petrov", "Nakamura",
            "Clef", "Gears", "Glass", "Kondraki", "Light", "Rights", "Crow", "King", "Mann", "Iceberg", "Sorts", "Everwood", "Talloran", "Vang"};
    /** Named staff are rarer and come with a special skill. */
    private static final String[] NAMED = {"\"Doc\" Halloway", "Agent Yoric", "Professor Marlowe", "Chief Brennan", "\"Sparks\" Delgado",
            "Dr. Amelia Vance", "Old Man Tuck", "Sergeant Okoye", "Nurse Ratchford", "Clerk Pemberton"};

    public static String random(Random random) {
        return FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
    }

    public static String named(Random random) {
        return NAMED[random.nextInt(NAMED.length)];
    }

    public static String dclass(Random random) {
        return "D-" + (1000 + random.nextInt(9000));
    }

    private StaffNames() {}
}
