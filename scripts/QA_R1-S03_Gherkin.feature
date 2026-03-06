Feature: R1-S03 - Employees, Shops and Country Scopes management (US-05/US-06/US-07 + CR-01)
  As a QA agent
  I want to validate employees and shops lifecycle
  So that sprint R1-S03 can close with DoD

  Background:
    Given the API is reachable
    And I am authenticated as admin "admin@rise.local"

  Scenario: Create and update shop
    When I create a shop with unique shop code
    Then the response status should be 201
    When I update the same shop
    Then the response status should be 200

  Scenario: Create and update employee
    Given a valid shop exists
    When I create an employee with unique employee id
    Then the response status should be 201
    When I update the same employee
    Then the response status should be 200

  Scenario: Logical deactivation of employee
    Given an active employee exists
    When I deactivate the employee
    Then the response status should be 204
    And the employee should not be available in active operational lists

  Scenario: Country manager scope restriction
    Given a country manager authenticated with limited country scope
    When the country manager tries to create employee on a shop outside scope
    Then the response status should be 403
    And the error message should mention scope restriction

  Scenario: Country scope lifecycle as GLOBAL_ADMIN
    Given I am authenticated as GLOBAL_ADMIN
    When I create a country scope with unique code
    Then the response status should be 201
    When I update the same country scope
    Then the response status should be 200
    When I deactivate the same country scope
    Then the response status should be 204

  Scenario: Country scope RBAC restriction for non GLOBAL_ADMIN
    Given I am authenticated as SYSTEM_ADMIN
    When I call POST /api/country-scopes
    Then the response status should be 403
