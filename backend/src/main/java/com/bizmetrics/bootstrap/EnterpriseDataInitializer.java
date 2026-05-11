package com.bizmetrics.bootstrap;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/** Invokes the single transactional production bootstrap command during startup. */
@Component("enterpriseDataInitializer")
@ConditionalOnProperty(name = "bizmetrics.bootstrap.mode", havingValue = "bootstrap")
@DependsOn("productionReadinessGuard")
public class EnterpriseDataInitializer {
    private final ProductionBootstrapCommand bootstrap;
    private final ProductionBootstrapCommand.Request request;

    public EnterpriseDataInitializer(
        ProductionBootstrapCommand bootstrap,
        @Value("${bizmetrics.bootstrap.admin-email:test@bizmetrics.com}") String adminEmail,
        @Value("${bizmetrics.bootstrap.admin-password:123456}") String adminPassword,
        @Value("${bizmetrics.bootstrap.admin-nickname:BizMetrics 管理员}") String adminNickname,
        @Value("${bizmetrics.security.password.min-length:12}") int passwordMinLength,
        @Value("${bizmetrics.security.password.require-complexity:false}") boolean passwordRequireComplexity,
        @Value("${bizmetrics.bootstrap.company-name:我的公司}") String companyName,
        @Value("${bizmetrics.bootstrap.company-credit-code:}") String companyCreditCode,
        @Value("${bizmetrics.bootstrap.company-industry:未设置}") String companyIndustry,
        @Value("${bizmetrics.bootstrap.company-taxpayer-type:未设置}") String companyTaxpayerType,
        @Value("${bizmetrics.bootstrap.company-currency:CNY}") String companyCurrency
    ) {
        this.bootstrap = bootstrap;
        this.request = new ProductionBootstrapCommand.Request(
            adminEmail,
            adminPassword,
            adminNickname,
            passwordMinLength,
            passwordRequireComplexity,
            companyName,
            companyCreditCode,
            companyIndustry,
            companyTaxpayerType,
            companyCurrency
        );
    }

    @PostConstruct
    void initialize() {
        bootstrap.execute(request);
    }
}
