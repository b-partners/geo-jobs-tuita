package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.SERVER_EXCEPTION;

import app.bpartners.geojobs.model.exception.ApiException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExcelAddressWriter implements Function<List<String>, File> {

  @Override
  public File apply(List<String> addresses) {
    try (Workbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Adresses");

      int rowIndex = 0;
      for (String address : addresses) {
        Row row = sheet.createRow(rowIndex++);
        Cell cell = row.createCell(0);
        cell.setCellValue(address);
      }

      File outputFile = File.createTempFile("unsupported addresses -", ".xlsx");
      try (FileOutputStream fos = new FileOutputStream(outputFile)) {
        workbook.write(fos);
      }

      return outputFile;
    } catch (IOException e) {
      throw new ApiException(SERVER_EXCEPTION, e);
    }
  }
}
