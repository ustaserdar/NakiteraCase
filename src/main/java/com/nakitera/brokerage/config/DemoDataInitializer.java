package com.nakitera.brokerage.config;

import com.nakitera.brokerage.domain.Asset;
import com.nakitera.brokerage.domain.Money;
import com.nakitera.brokerage.repository.AssetRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Profile("!test")
public class DemoDataInitializer implements ApplicationRunner {

    private final AssetRepository assetRepository;

    public DemoDataInitializer(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("customer-1", Money.TRY, "100000.000000", "100000.000000");
        seed("customer-1", "THYAO", "100.000000", "100.000000");
        seed("customer-1", "ASELS", "50.000000", "50.000000");
        seed("customer-2", Money.TRY, "50000.000000", "50000.000000");
        seed("customer-2", "THYAO", "20.000000", "20.000000");
    }

    private void seed(String customerId, String assetName, String size, String usableSize) {
        assetRepository.findByCustomerIdAndAssetName(customerId, assetName)
                .orElseGet(() -> assetRepository.save(new Asset(
                        customerId,
                        assetName,
                        new BigDecimal(size),
                        new BigDecimal(usableSize)
                )));
    }
}
