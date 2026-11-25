package com.example.exam.service;

import com.example.exam.model.User;
import com.example.exam.repository.UserRepository;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class StudentExcelImportService {
    private static final String STUDENT_NAME_HEADER = "student name";
    private static final String USERNAME_HEADER = "username";
    private static final String PASSWORD_HEADER = "password";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    public List<StudentImportRecord> importStudents(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Select an Excel file to upload.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || filename.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw new IllegalArgumentException("Upload an .xlsx or .xls Excel file.");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("The Excel file does not contain a worksheet.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("The worksheet must contain a header row.");
            }

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> headerColumns = readHeaders(headerRow, formatter, evaluator);
            int nameColumn = requiredColumn(headerColumns, STUDENT_NAME_HEADER);
            int usernameColumn = requiredColumn(headerColumns, USERNAME_HEADER);
            int passwordColumn = requiredColumn(headerColumns, PASSWORD_HEADER);

            List<StudentImportRecord> records = new ArrayList<>();
            Set<String> usernamesInFile = new HashSet<>();

            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String fullName = readCell(row, nameColumn, formatter, evaluator).trim();
                String username = readCell(row, usernameColumn, formatter, evaluator).trim();
                String password = readCell(row, passwordColumn, formatter, evaluator);
                if (fullName.isBlank() && username.isBlank() && password.isBlank()) {
                    continue;
                }

                String validationError = validateRow(fullName, username, password);
                if (validationError != null) {
                    records.add(new StudentImportRecord(rowIndex + 1, fullName, username, false, validationError));
                    continue;
                }

                if (!usernamesInFile.add(username)) {
                    records.add(new StudentImportRecord(rowIndex + 1, fullName, username, false,
                            "Username is duplicated in this file."));
                    continue;
                }

                if (userRepository.findByUsername(username).isPresent()) {
                    records.add(new StudentImportRecord(rowIndex + 1, fullName, username, false,
                            "Username already exists."));
                    continue;
                }

                User student = new User();
                student.setFullName(fullName);
                student.setUsername(username);
                student.setPassword(password);
                try {
                    userService.saveStudent(student);
                    records.add(new StudentImportRecord(rowIndex + 1, fullName, username, true,
                            "Account created."));
                } catch (DataIntegrityViolationException e) {
                    records.add(new StudentImportRecord(rowIndex + 1, fullName, username, false,
                            "Username already exists."));
                }
            }

            if (records.isEmpty()) {
                throw new IllegalArgumentException("No student data rows were found in the worksheet.");
            }
            return records;
        } catch (EncryptedDocumentException e) {
            throw new IllegalArgumentException("The uploaded file is not a valid, readable Excel workbook.", e);
        }
    }

    private Map<String, Integer> readHeaders(Row headerRow, DataFormatter formatter,
                                             FormulaEvaluator evaluator) {
        Map<String, Integer> headerColumns = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell, evaluator).trim().toLowerCase(Locale.ROOT);
            if (!header.isEmpty()) {
                headerColumns.putIfAbsent(header, cell.getColumnIndex());
            }
        }
        return headerColumns;
    }

    private int requiredColumn(Map<String, Integer> headerColumns, String header) {
        Integer column = headerColumns.get(header);
        if (column == null) {
            throw new IllegalArgumentException(
                    "The worksheet must include Student Name, Username, and Password columns.");
        }
        return column;
    }

    private String readCell(Row row, int columnIndex, DataFormatter formatter, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator);
    }

    private String validateRow(String fullName, String username, String password) {
        if (fullName.isBlank() || username.isBlank() || password.isBlank()) {
            return "Student Name, Username, and Password are required.";
        }
        if (fullName.length() > 255 || username.length() > 255) {
            return "Student Name and Username must be 255 characters or fewer.";
        }
        return null;
    }
}
