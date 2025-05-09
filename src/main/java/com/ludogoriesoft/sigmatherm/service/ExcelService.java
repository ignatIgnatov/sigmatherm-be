package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.entity.Product;
import com.ludogoriesoft.sigmatherm.entity.Supplier;
import com.ludogoriesoft.sigmatherm.repository.ProductRepository;
import com.ludogoriesoft.sigmatherm.repository.SupplierRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExcelService {

  private final ProductRepository productRepository;
  private final SupplierRepository supplierRepository;

  public void importProductOfferFromExcel(InputStream inputStream) throws IOException {
    Workbook workbook = new XSSFWorkbook(inputStream);
    Sheet sheet = workbook.getSheetAt(2);

    for (Row row : sheet) {
      if (row.getCell(4) == null) {
        break;
      }
      if (row.getRowNum() >= 0 && row.getRowNum() <= 4) {
        continue;
      }
      createProductInDb(row);
    }

    workbook.close();
  }

  public void importSuppliersFromExcel(InputStream inputStream) throws IOException {
    Workbook workbook = new XSSFWorkbook(inputStream);
    Sheet sheet = workbook.getSheetAt(2);

    for (Row row : sheet) {
      if (row.getCell(4) == null) {
        break;
      }
      if (row.getRowNum() >= 0 && row.getRowNum() <= 4) {
        continue;
      }
      createSupplierInDb(row);
    }

    workbook.close();
  }

  private void  createSupplierInDb(Row row) {
    if (!supplierRepository.existsByNameIgnoreCase(row.getCell(0).getStringCellValue())) {
      Supplier supplier = new Supplier();
      supplier.setName(row.getCell(0).getStringCellValue());
      String marginString = row.getCell(0).getStringCellValue();
      double margin = Double.parseDouble(marginString);
      supplier.setPriceMargin(BigDecimal.valueOf(margin));
      supplierRepository.save(supplier);
    }
  }

  private void createProductInDb(Row row) {
    if (!productRepository.existsById(row.getCell(4).getStringCellValue())) {
      Product product = new Product();
      product.setId(row.getCell(4).getStringCellValue());
      product.setName(row.getCell(0).getStringCellValue());
      String supplierName = row.getCell(2).getStringCellValue();
      Supplier supplier = supplierRepository.findByNameIgnoreCase(supplierName);
      product.setSupplier(supplier);
      String basePrice = row.getCell(7).getStringCellValue();
      double price = Double.parseDouble(basePrice);
      product.setBasePrice(BigDecimal.valueOf(price));
      String availabilityString = row.getCell(15).getStringCellValue();
      int availability = Integer.parseInt(availabilityString);
      product.setAvailability(availability);
      productRepository.save(product);
    }
  }
}
