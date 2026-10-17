package app.gym.settings;

import jakarta.validation.Valid;
import java.time.ZoneId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    public record SettingsDto(String gymName, String timezone, int duplicateWindowMinutes, int dropOffDays,
                              int expiringSoonDays, boolean memberCanEditEmail) {
        static SettingsDto of(GymSettings s) {
            return new SettingsDto(s.gymName(), s.zone().getId(), s.duplicateWindowMinutes(), s.dropOffDays(),
                    s.expiringSoonDays(), s.memberCanEditEmail());
        }
    }

    private final GymSettingsService service;

    public SettingsController(GymSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.owner()")
    public SettingsDto get() {
        return SettingsDto.of(service.get());
    }

    @PutMapping
    @PreAuthorize("@perm.owner()")
    public SettingsDto update(@Valid @RequestBody GymSettingsService.UpdateSettingsRequest req) {
        return SettingsDto.of(service.update(req));
    }
}
