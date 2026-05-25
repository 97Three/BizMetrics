package com.bizmetrics.workforce.api;

import com.bizmetrics.platform.identity.ActorContext;
import com.bizmetrics.platform.identity.CurrentActor;
import com.bizmetrics.platform.product.RequiresProductModule;
import com.bizmetrics.workforce.application.WorkforceCostApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workforce-cost")
@RequiresProductModule("workforce-cost")
public class WorkforceCostController {
    private final WorkforceCostApplicationService service;

    public WorkforceCostController(WorkforceCostApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public WorkforceCostView view(
        @CurrentActor ActorContext actor,
        @RequestParam(value = "companyId", required = false) Long companyId,
        @RequestParam(value = "period", required = false) String period
    ) {
        return service.view(actor, companyId, period);
    }
}
