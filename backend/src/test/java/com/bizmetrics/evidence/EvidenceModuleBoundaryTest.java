package com.bizmetrics.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bizmetrics.common.PagedResponse;
import com.bizmetrics.evidence.api.ReceiptController;
import com.bizmetrics.evidence.api.ReceiptCreateRequest;
import com.bizmetrics.evidence.api.ReceiptQueryRequest;
import com.bizmetrics.evidence.api.ReceiptSummaryRequest;
import com.bizmetrics.evidence.api.ReceiptUpdateRequest;
import com.bizmetrics.evidence.api.ReceiptUploadRequest;
import com.bizmetrics.evidence.application.ReceiptApplicationService;
import com.bizmetrics.evidence.application.ReceiptApprovalStatusService;
import com.bizmetrics.evidence.application.ReceiptBatchUploadResult;
import com.bizmetrics.evidence.application.ReceiptCreateCommand;
import com.bizmetrics.evidence.application.ReceiptFileHashRepository;
import com.bizmetrics.evidence.application.ReceiptListQuery;
import com.bizmetrics.evidence.application.ReceiptStorageAuditService;
import com.bizmetrics.evidence.application.ReceiptStorageGuard;
import com.bizmetrics.evidence.application.ReceiptStorageReferenceRepository;
import com.bizmetrics.evidence.application.ReceiptStorageUsageRepository;
import com.bizmetrics.evidence.application.ReceiptUpdateCommand;
import com.bizmetrics.evidence.application.ReceiptUploadCommand;
import com.bizmetrics.evidence.application.ReceiptVoucherRepository;
import com.bizmetrics.evidence.domain.ReceiptSummary;
import com.bizmetrics.evidence.domain.ReceiptVoucher;
import com.bizmetrics.evidence.infrastructure.MyBatisReceiptFileHashRepository;
import com.bizmetrics.evidence.infrastructure.MyBatisReceiptStorageReferenceRepository;
import com.bizmetrics.evidence.infrastructure.MyBatisReceiptStorageUsageRepository;
import com.bizmetrics.evidence.infrastructure.MyBatisReceiptVoucherRepository;
import com.bizmetrics.operations.application.TransactionLinkQuery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

class EvidenceModuleBoundaryTest {
    @Test
    void receiptUseCaseLivesBehindEvidenceApplicationPorts() throws Exception {
        assertEquals("com.bizmetrics.evidence.api", ReceiptController.class.getPackageName());
        assertEquals("com.bizmetrics.evidence.application", ReceiptApplicationService.class.getPackageName());
        assertTrue(ReceiptApprovalStatusService.class.isAssignableFrom(ReceiptApplicationService.class));

        Set<String> dependencyTypes = Arrays.stream(ReceiptApplicationService.class.getDeclaredFields())
            .map(field -> field.getType().getName())
            .collect(Collectors.toSet());
        assertTrue(dependencyTypes.contains(ReceiptVoucherRepository.class.getName()));
        assertTrue(dependencyTypes.contains(ReceiptFileHashRepository.class.getName()));
        assertTrue(dependencyTypes.contains(ReceiptStorageGuard.class.getName()));
        assertTrue(dependencyTypes.contains(TransactionLinkQuery.class.getName()));
        assertFalse(dependencyTypes.contains("org.springframework.jdbc.core.JdbcTemplate"));
        assertFalse(dependencyTypes.contains(MyBatisReceiptVoucherRepository.class.getName()));
        assertFalse(dependencyTypes.contains(MyBatisReceiptFileHashRepository.class.getName()));
        assertFalse(dependencyTypes.contains(MyBatisReceiptStorageUsageRepository.class.getName()));
        assertFalse(dependencyTypes.contains("com.bizmetrics.operations.application.TransactionQueryRepository"));
        String source = Files.readString(Path.of(
            "src/main/java/com/bizmetrics/evidence/application/ReceiptApplicationService.java"
        ));
        assertFalse(source.contains("TransactionQueryRepository"));
        assertFalse(source.contains("TransactionRecord"));
    }

    @Test
    void oldHorizontalReceiptEntrypointsCannotReturn() {
        assertFalse(Files.exists(Path.of("src/main/java/com/bizmetrics/service/ReceiptService.java")));
        assertFalse(Files.exists(Path.of("src/main/java/com/bizmetrics/controller/ReceiptController.java")));
        assertFalse(Files.exists(Path.of(
            "src/main/java/com/bizmetrics/evidence/infrastructure/ReceiptVoucherRepository.java"
        )));
        assertEquals("com.bizmetrics.evidence.application", ReceiptVoucherRepository.class.getPackageName());
        assertEquals("com.bizmetrics.evidence.application", ReceiptStorageUsageRepository.class.getPackageName());
        assertEquals("com.bizmetrics.evidence.application", ReceiptStorageReferenceRepository.class.getPackageName());
        assertEquals("com.bizmetrics.evidence.infrastructure", MyBatisReceiptVoucherRepository.class.getPackageName());
        assertEquals("com.bizmetrics.evidence.infrastructure", MyBatisReceiptStorageUsageRepository.class.getPackageName());
        assertEquals(
            "com.bizmetrics.evidence.infrastructure",
            MyBatisReceiptStorageReferenceRepository.class.getPackageName()
        );
        Set<String> auditDependencies = Arrays.stream(ReceiptStorageAuditService.class.getDeclaredFields())
            .map(field -> field.getType().getName())
            .collect(Collectors.toSet());
        assertTrue(auditDependencies.contains(ReceiptStorageReferenceRepository.class.getName()));
        assertFalse(auditDependencies.contains("org.springframework.jdbc.core.JdbcTemplate"));
    }

    @Test
    void receiptDomainAndContractsCannotReturnToSharedModelsOrMaps() throws Exception {
        assertEquals("com.bizmetrics.evidence.domain", ReceiptVoucher.class.getPackageName());
        assertEquals(
            ReceiptVoucher.class,
            ReceiptApplicationService.class
                .getDeclaredMethod("create", String.class, ReceiptCreateCommand.class)
                .getReturnType()
        );
        assertEquals(
            ReceiptVoucher.class,
            ReceiptApplicationService.class
                .getDeclaredMethod("update", String.class, long.class, ReceiptUpdateCommand.class)
                .getReturnType()
        );
        assertFalse(Arrays.stream(ReceiptApplicationService.class.getDeclaredMethods())
            .filter(method -> Set.of("list", "create", "update", "upload", "batchUpload").contains(method.getName()))
            .flatMap(method -> Arrays.stream(method.getParameterTypes()))
            .anyMatch(Map.class::equals));
        assertFalse(Arrays.stream(com.bizmetrics.domain.Models.class.getDeclaredClasses())
            .anyMatch(type -> type.getSimpleName().equals("ReceiptVoucher")));
        assertEquals(ReceiptCreateCommand.class, ReceiptCreateRequest.class.getDeclaredMethod("toCommand").getReturnType());
        assertEquals(ReceiptUpdateCommand.class, ReceiptUpdateRequest.class.getDeclaredMethod("toCommand").getReturnType());
        assertEquals(ReceiptUploadCommand.class, ReceiptUploadRequest.class.getDeclaredMethod("toCommand").getReturnType());
        assertEquals(ReceiptListQuery.class, ReceiptQueryRequest.class.getDeclaredMethod("toQuery").getReturnType());
        assertEquals(
            PagedResponse.class,
            ReceiptApplicationService.class
                .getDeclaredMethod("list", String.class, ReceiptListQuery.class)
                .getReturnType()
        );
        assertEquals(
            ReceiptSummary.class,
            ReceiptApplicationService.class
                .getDeclaredMethod("summary", String.class, Long.class)
                .getReturnType()
        );
        assertEquals(
            ReceiptSummary.class,
            ReceiptController.class
                .getDeclaredMethod("summary", String.class, ReceiptSummaryRequest.class)
                .getReturnType()
        );
        assertEquals(
            ReceiptVoucher.class,
            ReceiptApplicationService.class
                .getDeclaredMethod("upload", String.class, MultipartFile.class, ReceiptUploadCommand.class)
                .getReturnType()
        );
        assertEquals(
            ReceiptBatchUploadResult.class,
            ReceiptApplicationService.class
                .getDeclaredMethod("batchUpload", String.class, List.class, ReceiptUploadCommand.class)
                .getReturnType()
        );
    }
}
