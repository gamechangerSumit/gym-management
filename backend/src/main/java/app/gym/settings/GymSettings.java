package app.gym.settings;

import java.time.ZoneId;

public record GymSettings(String gymName, ZoneId zone, int duplicateWindowMinutes, int dropOffDays,
                          int expiringSoonDays, boolean memberCanEditEmail) {
}
