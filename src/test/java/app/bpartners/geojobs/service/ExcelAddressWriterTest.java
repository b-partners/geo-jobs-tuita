package app.bpartners.geojobs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ExcelAddressWriterTest {
  ExcelAddressWriter subject = new ExcelAddressWriter();

  @SneakyThrows
  @Test
  void convert_addresses_to_excel() {
    var expected =
        new ClassPathResource("excel/excepted converted addresses from string.xlsx").getFile();

    var actual = subject.apply(List.of("Lyon", "Paris"));

    assertEquals(expected.length(), actual.length());
    assertExcelEquals(expected, actual);
  }

  @SneakyThrows
  private void assertExcelEquals(File expected, File actual) {
    try (Workbook wbExpected = new XSSFWorkbook(new FileInputStream(expected));
        Workbook wbActual = new XSSFWorkbook(new FileInputStream(actual))) {

      Sheet sheetExp = wbExpected.getSheetAt(0);
      Sheet sheetAct = wbActual.getSheetAt(0);

      for (int i = 0; i <= sheetExp.getLastRowNum(); i++) {
        Row rowExp = sheetExp.getRow(i);
        Row rowAct = sheetAct.getRow(i);

        // gérer lignes null
        if (rowExp == null && rowAct == null) continue;
        assertNotNull(rowAct, "Ligne " + i + " absente dans le fichier actual");

        for (int j = 0; j < rowExp.getLastCellNum(); j++) {
          Cell cExp = rowExp.getCell(j);
          Cell cAct = rowAct.getCell(j);

          if (cExp == null && cAct == null) continue;

          assertNotNull(cAct, "Cellule absente à (" + i + "," + j + ")");

          assertEquals(
              cExp.getStringCellValue(),
              cAct.getStringCellValue(),
              "Valeur différente à la cellule (" + i + "," + j + ")");
        }
      }
    }
  }
}
