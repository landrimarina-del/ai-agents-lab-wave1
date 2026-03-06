Feature: R1-S02 - User state, BU scope and Business Unit management (US-03/US-04/US-08)
  As a QA agent
  I want to validate R1-S02 acceptance criteria
  So that DoD closure is supported

  Background:
    Given the API is reachable
    And I am authenticated as admin "admin@rise.local"

  Scenario: Deactivate and reactivate user
    Given a user exists with id "2"
    When I deactivate user "2"
    Then the response status should be 200
    And the user should be marked as deactivated
    When I reactivate user "2"
    Then the response status should be 200
    And the user should be marked as active

  Scenario: Update country manager business unit scope
    Given a country manager exists with id "2"
    And business units exist with ids "1" and "2"
    When I update user "2" business units to "1,2"
    Then the response status should be 200 or 204
    And the country manager scope should include business units "1" and "2"

  Scenario: Business unit CRUD and delete blocked by dependencies
    Given a business unit payload is valid
    When I create a business unit
    Then the response status should be 201 or 200
    When I update the created business unit
    Then the response status should be 200
    When I try to delete the business unit with dependencies
    Then the response status should be 400 or 409
    And the error should indicate dependency constraint
