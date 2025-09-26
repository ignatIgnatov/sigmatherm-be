package com.ludogoriesoft.sigmatherm.service;

import com.ludogoriesoft.sigmatherm.model.Product;
import com.ludogoriesoft.sigmatherm.model.Brand;
import com.ludogoriesoft.sigmatherm.repository.ProductRepository;
import com.ludogoriesoft.sigmatherm.repository.BrandRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

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
  private final BrandRepository brandRepository;

  private static final String OFFER_DIR = "/app/offers/";

  private static final String OFFER_FILE = "offer.xlsx";

  public void createExcelOffer(List<Product> productEntities) throws IOException {
    File directory = new File(OFFER_DIR);
    if (!directory.exists()) {
      directory.mkdirs();
    }

    File offerFile = new File(OFFER_DIR + OFFER_FILE);

    if (offerFile.exists()) {
      boolean deleted = offerFile.delete();
      if (!deleted) {
        throw new IOException("Failed to delete old offer file.");
      }
    }


    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Offers");

      Row headerRow = sheet.createRow(0);
      String[] columns = {"id", "status", "sale_price", "vat_id", "handling_time", "stock"};
      for (int i = 0; i < columns.length; i++) {
        headerRow.createCell(i).setCellValue(columns[i]);
      }

      int rowNum = 1;
      for (Product p : productEntities) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(p.getId());
        row.createCell(1).setCellValue(p.getStatus());
        row.createCell(2).setCellValue(String.valueOf(p.getPrice().getBasePrice()));
        row.createCell(3).setCellValue(p.getVatId());
        row.createCell(4).setCellValue(p.getHandlingTime());
        row.createCell(5).setCellValue(p.getStock());
      }

      try (FileOutputStream fileOut = new FileOutputStream(offerFile)) {
        workbook.write(fileOut);
      }
    }
  }

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
    Sheet sheet = workbook.getSheetAt(0);

    for (Row row : sheet) {
      if (row.getCell(0) == null) {
        break;
      }
      if (row.getRowNum() == 0) {
        continue;
      }
      createSupplierInDb(row);
    }

    workbook.close();
  }

  private void  createSupplierInDb(Row row) {
    if (!brandRepository.existsByNameIgnoreCase(row.getCell(0).getStringCellValue())) {
      Brand brand = new Brand();
      brand.setName(row.getCell(0).getStringCellValue());
      String marginString = row.getCell(1).getStringCellValue();
      double margin = Double.parseDouble(marginString);
      brand.setPriceMargin(BigDecimal.valueOf(margin));
      brandRepository.save(brand);
    }
  }

  private void createProductInDb(Row row) {
    if (!productRepository.existsById(row.getCell(4).getStringCellValue())) {
      Product product = new Product();
      product.setId(row.getCell(4).getStringCellValue());
      product.setName(row.getCell(0).getStringCellValue());
      String supplierName = row.getCell(2).getStringCellValue();
      Brand brand = brandRepository.findByNameIgnoreCase(supplierName);
      product.setBrand(brand);
      String basePrice = row.getCell(7).getStringCellValue();
      product.getPrice().setBasePrice(BigDecimal.valueOf(Double.parseDouble(basePrice)));
      String availabilityString = row.getCell(15).getStringCellValue();
      int availability = Integer.parseInt(availabilityString);
      product.setStock(availability);
      product.setVatId(row.getCell(12).getStringCellValue());
      product.setHandlingTime(row.getCell(16).getStringCellValue());
      productRepository.save(product);
    }
  }
}
