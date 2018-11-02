import org.apache.poi.ss.usermodel.*;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Command;

@Command(description="Convert an excel worksheet into a set of JSON objects", name="x2j", mixinStandardHelpOptions = true, version = "0.1")
public class Excel2Json implements Callable<Void> {
    @Parameters(index = "0", description = "The excel file to load")
    private File file;

    @Option(names = {"-w", "--worksheet"}, description = "The worksheet to convert")
    private String worksheet;

    @Option(names = {"-o", "--output"}, required = true, description = "The output file")
    private File outputFile;

    @Option(names = {"-c", "--columns"}, split = ",")
    private Set<String> columns;

    @Option(names = {"-e", "--exclude"}, description = "Exclude the columns denoted by -c", defaultValue = "false")
    private boolean isExclude = false;

    @Option(names = {"-r", "--headerRow"}, description = "The row index to expect a header row", defaultValue = "-1")
    private int headerRowIndex;

    private final Logger log = LoggerFactory.getLogger("Excel2Json");

    private Workbook workbook;
    private FormulaEvaluator eval;
    private DataFormatter formatter;
    private JSONWriter jsonWriter;

    public static void main(String[] args) {
        CommandLine.call(new Excel2Json(), args);
    }

    @Override
    public Void call() throws Exception {
        log.info("Excel2Json Starting");

        if(!file.exists()) {
            log.error("File "+file.getName()+" does not exist!");
            return null;
        }

        if(file.isDirectory()) {
            log.error(file.getName()+" is a directory!");
            return null;
        }

        if(columns != null) {
            if(headerRowIndex < 0) {
                log.error("A header row must be specified when using --columns");
                return null;
            }

            if(worksheet == null || worksheet.isEmpty()) {
                log.error("A worksheet must be specified when using --columns");
                return null;
            }
        }

        if(outputFile.isDirectory()) {
            log.error("The output file " + outputFile + " is a directory");
            return null;
        }

        try(final FileWriter writer = new FileWriter(outputFile)) {
            jsonWriter = new JSONWriter(writer);
            log.info("Loading workbook "+file.getName());
            try(FileInputStream fis = new FileInputStream(file)) {
                workbook = WorkbookFactory.create(fis);
                eval = workbook.getCreationHelper().createFormulaEvaluator();
                formatter = new DataFormatter();
            }

            if(worksheet != null && !worksheet.isEmpty()) {
                final Sheet s = workbook.getSheet(worksheet);
                if(s == null) {
                    log.error("Workbook " + file.getName() + " has no worksheet " + worksheet);
                    return null;
                }

                log.info("Processing worksheet " + worksheet);
                convertToJson(s);
            } else {
                for(int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    log.info("Processing worksheet "+workbook.getSheetName(i));
                    convertToJson(workbook.getSheetAt(i));
                }
            }
        }

        log.info("Done!");
        return null;
    }

    private void convertToJson(Sheet s) {
        final int rowCnt = s.getPhysicalNumberOfRows();

        final Map<Integer, String> columnMap;
        if(headerRowIndex < 0) {
            columnMap = Collections.emptyMap();
        } else {
            columnMap = new HashMap<>();
            final Row headerRow = s.getRow(headerRowIndex);
            for(int i = headerRow.getFirstCellNum(); i <= headerRow.getLastCellNum(); i++) {
                final String headerVal = formatCell(headerRow.getCell(i));
                if(headerVal != null && !headerVal.isEmpty()) {
                    columnMap.put(i, headerVal);
                }
            }
        }

        jsonWriter.array();
        for(int i = headerRowIndex < 0 ? 0 : headerRowIndex+1; i<rowCnt; i++) {
            try {
                convertRow(s.getRow(i), columnMap);
            } catch(Exception whatever) {
                log.error("It broke: ", whatever);
            }
        }
        jsonWriter.endArray();
    }

    private void convertRow(Row row, Map<Integer, String> columnMap) {
        final StringBuilder sb = new StringBuilder();
        jsonWriter.object();
        for(int i = row.getFirstCellNum(); i >= 0 && i <= row.getLastCellNum(); i++) {
            final Cell cell = row.getCell(i);

            if(shouldInclude(i, columnMap)) {
                final String cellValueString = formatCell(cell);
                sb.append(sb.length() > 0 ? ", " : "").append(cellValueString);
                jsonWriter.key(columnMap.get(i));
                jsonWriter.value(cellValueString);
            }
        }
        jsonWriter.endObject();

        log.info(sb.toString());
    }

    private String formatCell(Cell c) {
        return formatter.formatCellValue(c, eval);
    }

    private boolean shouldInclude(int cellIndex, Map<Integer, String> columnMap) {
        final String columnName = columnMap.get(cellIndex);
        if(columnName == null || columnName.isEmpty()) {
            return false;
        }

        final boolean isRequested = columns.contains(columnName);
        return isExclude != isRequested;
    }
}
