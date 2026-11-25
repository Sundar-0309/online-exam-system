package com.example.exam.service;

public class StudentImportRecord {
    private final int rowNumber;
    private final String fullName;
    private final String username;
    private final boolean success;
    private final String message;

    public StudentImportRecord(int rowNumber, String fullName, String username,
                               boolean success, String message) {
        this.rowNumber = rowNumber;
        this.fullName = fullName;
        this.username = username;
        this.success = success;
        this.message = message;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
