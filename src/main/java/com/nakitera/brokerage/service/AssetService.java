package com.nakitera.brokerage.service;

import com.nakitera.brokerage.domain.Asset;
import com.nakitera.brokerage.domain.Money;
import com.nakitera.brokerage.exception.ApiException;
import com.nakitera.brokerage.exception.ErrorCodes;
import com.nakitera.brokerage.repository.AssetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final AccessControlService accessControlService;

    public AssetService(AssetRepository assetRepository, AccessControlService accessControlService) {
        this.assetRepository = assetRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public List<Asset> listByCustomer(String customerId) {
        accessControlService.assertCanAccessCustomer(customerId);
        return assetRepository.findByCustomerIdOrderByAssetNameAsc(customerId);
    }

    public Asset requireAsset(String customerId, String assetName) {
        return assetRepository.findByCustomerIdAndAssetNameForUpdate(customerId, assetName)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        ErrorCodes.ASSET_NOT_FOUND,
                        "Asset '%s' was not found for customer '%s'".formatted(assetName, customerId)
                ));
    }

    public Asset requireTry(String customerId) {
        return requireAsset(customerId, Money.TRY);
    }
}
