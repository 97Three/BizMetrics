package com.bizmetrics.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bizmetrics.approval.application.ApprovalApplicationService;
import com.bizmetrics.approval.application.ApprovalEntityGateway;
import com.bizmetrics.approval.application.ApprovalRepository;
import com.bizmetrics.approval.infrastructure.MyBatisApprovalEntityGateway;
import com.bizmetrics.approval.infrastructure.MyBatisApprovalRepository;
import com.bizmetrics.evidence.application.ReceiptApprovalStatusService;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ApprovalModuleBoundaryTest {
    @Test
    void approvalApplicationServiceCannotReturnToHorizontalServicePackage() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.bizmetrics.service.ApprovalService"));
        assertEquals(
            "com.bizmetrics.approval.application",
            ApprovalApplicationService.class.getPackageName()
        );

        Set<String> dependencyTypes = Arrays.stream(ApprovalApplicationService.class.getDeclaredFields())
            .map(field -> field.getType().getName())
            .collect(Collectors.toSet());
        assertTrue(dependencyTypes.contains(ApprovalEntityGateway.class.getName()));
        assertTrue(dependencyTypes.contains(ApprovalRepository.class.getName()));
        assertFalse(dependencyTypes.contains("org.springframework.jdbc.core.JdbcTemplate"));
        assertFalse(dependencyTypes.contains("com.bizmetrics.service.ReceiptService"));
        assertFalse(dependencyTypes.contains("com.bizmetrics.evidence.infrastructure.ReceiptVoucherRepository"));
        assertEquals("com.bizmetrics.approval.application", ApprovalRepository.class.getPackageName());
        assertEquals("com.bizmetrics.approval.infrastructure", MyBatisApprovalRepository.class.getPackageName());
    }

    @Test
    void approvalAdapterUsesTheNarrowReceiptStatusContract() {
        Set<String> dependencyTypes = Arrays.stream(MyBatisApprovalEntityGateway.class.getDeclaredFields())
            .map(field -> field.getType().getName())
            .collect(Collectors.toSet());

        assertTrue(dependencyTypes.contains(ReceiptApprovalStatusService.class.getName()));
        assertFalse(dependencyTypes.contains("com.bizmetrics.evidence.application.ReceiptApplicationService"));
        assertFalse(dependencyTypes.contains("com.bizmetrics.service.ReceiptService"));
    }
}
