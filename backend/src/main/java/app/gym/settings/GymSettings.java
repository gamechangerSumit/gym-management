package app.gym.settings;

import java.time.ZoneId;

public record GymSettings(String gymName, ZoneId zone, int duplicateWindowMinutes, int dropOffDays,
                          int expiringSoonDays, int noShowThresholdDays, int weeklyVisitGoal, boolean memberCanEditEmail) {
    public GymSettings(String gymName, ZoneId zone, int duplicateWindowMinutes, int dropOffDays,
                       int expiringSoonDays, int weeklyVisitGoal, boolean memberCanEditEmail) {
        this(gymName, zone, duplicateWindowMinutes, dropOffDays, expiringSoonDays, 10, weeklyVisitGoal, memberCanEditEmail);
    }

    public GymSettings(String gymName, ZoneId zone, int duplicateWindowMinutes, int dropOffDays,
                       int expiringSoonDays, boolean memberCanEditEmail) {
        this(gymName, zone, duplicateWindowMinutes, dropOffDays, expiringSoonDays, 10, 3, memberCanEditEmail);
    }
}
