package app.gym.addon;

import app.gym.addon.AddOnService.AddOnDto;
import app.gym.addon.AddOnService.AddOnRequest;
import app.gym.addon.AddOnService.CreateOrderRequest;
import app.gym.addon.AddOnService.OrderDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AddOnController {
    private final AddOnService service;
    public AddOnController(AddOnService service) { this.service = service; }

    @GetMapping("/addons")
    @PreAuthorize("isAuthenticated()")
    public List<AddOnDto> catalog() { return service.catalog(false); }

    @GetMapping("/admin/addons")
    @PreAuthorize("@perm.owner()")
    public List<AddOnDto> adminCatalog() { return service.catalog(true); }

    @PostMapping("/addons")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.owner()")
    public AddOnDto create(@Valid @RequestBody AddOnRequest req) { return service.create(req); }

    @PutMapping("/addons/{id}")
    @PreAuthorize("@perm.owner()")
    public AddOnDto update(@PathVariable long id, @Valid @RequestBody AddOnRequest req) { return service.update(id, req); }

    /** Remove an add-on from the member catalogue while preserving order history. */
    @DeleteMapping("/addons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.owner()")
    public void delete(@PathVariable long id) { service.delete(id); }

    @PostMapping("/me/addon-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.member()")
    public OrderDto createMemberOrder(@Valid @RequestBody CreateOrderRequest req) { return service.createMemberOrder(req); }

    @GetMapping("/me/addon-orders")
    @PreAuthorize("@perm.member()")
    public List<OrderDto> memberOrders() { return service.memberOrders(); }

    @PostMapping("/me/addon-orders/{id}/cancel")
    @PreAuthorize("@perm.member()")
    public void cancelMemberOrder(@PathVariable long id) { service.cancelMemberOrder(id); }

    @GetMapping("/addon-orders")
    @PreAuthorize("@perm.staffOrOwner()")
    public List<OrderDto> allOrders() { return service.allOrders(); }

    @PostMapping("/addon-orders/{id}/cancel")
    @PreAuthorize("@perm.staffOrOwner()")
    public void cancelOrder(@PathVariable long id) { service.cancelOrder(id, null); }

    @PostMapping("/addon-orders/{id}/fulfill")
    @PreAuthorize("@perm.staffOrOwner()")
    public OrderDto fulfill(@PathVariable long id) { return service.fulfill(id); }

    @PostMapping("/addon-orders/{id}/session")
    @PreAuthorize("@perm.staffOrOwner()")
    public OrderDto recordSession(@PathVariable long id) { return service.recordSession(id); }
}
