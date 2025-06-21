package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.dto.magento.MagentoProductResponseDto;
import com.ludogoriesoft.sigmatherm.dto.magento.MagentoProductSalesDto;
import com.ludogoriesoft.sigmatherm.entity.Synchronization;
import com.ludogoriesoft.sigmatherm.entity.enums.Platform;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MagentoService {

    private final SynchronizationService synchronizationService;
    private final ProductService productService;
    private final ModelMapper modelMapper;

    public List<MagentoProductResponseDto> getAllSyncProducts() {
        return productService.getAllProducts().stream().map(product -> modelMapper.map(product, MagentoProductResponseDto.class)).toList();
    }

    @Transactional
    public ResponseEntity<String> receiveProductSales(List<MagentoProductSalesDto> products) {
        try {
            if (products.isEmpty()) {
                log.error("Empty list of product sales");
                return ResponseEntity.badRequest().body("Empty list of product sales");
            }

            Synchronization synchronization = synchronizationService.createSync(Platform.Magento);
            for (MagentoProductSalesDto product : products) {
                productService.setSync(product.getId(), synchronization);
                productService.reduceAvailabilityByOrder(product.getId(), product.getSales());
            }
        } catch (Exception e) {
            log.error("Fail to sync the sales from Magenoto: " + e.getMessage());
            return ResponseEntity.badRequest().body("Fail to sync the sales");
        }
        return ResponseEntity.ok().body("Product sales received successfully!");
    }
}
