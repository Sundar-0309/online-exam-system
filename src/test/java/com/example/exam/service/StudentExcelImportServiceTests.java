package com.example.exam.service;

import com.example.exam.model.User;
import com.example.exam.repository.UserRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class StudentExcelImportServiceTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private StudentExcelImportService importService;

    @Test
    void importsRowsAndReportsExistingAndInFileDuplicateUsernames() throws IOException {
        when(userRepository.findByUsername("existing")).thenReturn(Optional.of(new User()));
        when(userRepository.findByUsername("fresh")).thenReturn(Optional.empty());
        MockMultipartFile file = excelFile(
                new String[]{"Username", "Password", "Student Name"},
                new String[][]{
                        {"existing", "secret1", "Already Exists"},
                        {"fresh", " secret2 ", "Fresh Student"},
                        {"fresh", "secret3", "Duplicate In File"}
                });

        List<StudentImportRecord> results = importService.importStudents(file);

        assertEquals(3, results.size());
        assertFalse(results.get(0).isSuccess());
        assertTrue(results.get(1).isSuccess());
        assertFalse(results.get(2).isSuccess());
        assertEquals("Username already exists.", results.get(0).getMessage());
        assertEquals("Username is duplicated in this file.", results.get(2).getMessage());
        ArgumentCaptor<User> studentCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).saveStudent(studentCaptor.capture());
        assertEquals("fresh", studentCaptor.getValue().getUsername());
        assertEquals(" secret2 ", studentCaptor.getValue().getPassword());
    }

    @Test
    void rejectsWorkbookMissingRequiredHeadersWithoutCreatingAccounts() throws IOException {
        MockMultipartFile file = excelFile(
                new String[]{"Name", "Username", "Password"},
                new String[][]{{"Student", "student01", "secret"}});

        assertThrows(IllegalArgumentException.class, () -> importService.importStudents(file));
        verify(userService, never()).saveStudent(any(User.class));
    }

    private MockMultipartFile excelFile(String[] headers, String[][] data) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Students");
            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                headerRow.createCell(column).setCellValue(headers[column]);
            }
            for (int rowIndex = 0; rowIndex < data.length; rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < data[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(data[rowIndex][column]);
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file", "students.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }
}
