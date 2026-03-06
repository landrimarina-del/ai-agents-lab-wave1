package com.rise.backend.employee;

public record EmployeeResponse(
        Integer id,
        String employeeId,
        String fullName,
        String email,
        Integer shopId,
        String shopCode,
        String countryCode,
        boolean active
) {
}
