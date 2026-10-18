package app.gym.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/no-shows")
@PreAuthorize("@perm.staffOrOwner()")
public class NoShowController {
    private final NoShowService service;
    public NoShowController(NoShowService service) { this.service = service; }

    @GetMapping
    public List<NoShowService.NoShowCase> list() { return service.openCases(); }

    @PostMapping("/scan")
    public ScanResponse scan(@RequestParam(defaultValue = "10") @Min(1) @Max(365) int threshold) {
        int affected = service.scan(threshold);
        return new ScanResponse(affected, threshold);
    }

    @PostMapping("/{id}/outcome")
    public void outcome(@PathVariable long id, @Valid @RequestBody NoShowService.OutcomeRequest request) {
        service.recordOutcome(id, request);
    }

    public record ScanResponse(int affected, int thresholdDays) {}
}
