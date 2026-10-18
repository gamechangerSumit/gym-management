package app.gym.renewal;

import app.gym.renewal.RenewalOrderService.CreateRequest;
import app.gym.renewal.RenewalOrderService.RenewalOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RenewalOrderController {
    private final RenewalOrderService service;
    public RenewalOrderController(RenewalOrderService service){this.service=service;}

    @PostMapping("/me/renewal-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.member()")
    public RenewalOrder createForMember(@Valid @RequestBody CreateRequest req){return service.createForCurrentMember(req);}

    @GetMapping("/me/renewal-orders")
    @PreAuthorize("@perm.member()")
    public List<RenewalOrder> myOrders(){return service.myOrders();}

    @PostMapping("/me/renewal-orders/{id}/cancel")
    @PreAuthorize("@perm.member()")
    public void cancelMine(@PathVariable long id){service.cancelForCurrentMember(id);}

    @GetMapping("/renewal-orders")
    @PreAuthorize("@perm.staffOrOwner()")
    public List<RenewalOrder> all(){return service.allOrders();}

    @PostMapping("/renewal-orders/{id}/cancel")
    @PreAuthorize("@perm.manageMemberships()")
    public void cancel(@PathVariable long id){service.cancel(id,null);}
}
