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
    Sheet sheet = workbook.getSheetAt(0);

    for (Row row : sheet) {
      if (row.getRowNum() == 0) {
        continue;
      }
      createProductInDb(row);
    }

    workbook.close();
  }

  private void createProductInDb(Row row) {
    Supplier supplier =
        supplierRepository.findByNameIgnoreCase(row.getCell(14).getStringCellValue());
    Product product = new Product();
    product.setId(row.getCell(1).getStringCellValue());
    product.setName(row.getCell(2).getStringCellValue());
    product.setSupplier(supplier);
    product.setBasePrice(BigDecimal.valueOf(row.getCell(8).getNumericCellValue()));
    product.setAvailability((int) row.getCell(7).getNumericCellValue());
    productRepository.save(product);
  }
}
