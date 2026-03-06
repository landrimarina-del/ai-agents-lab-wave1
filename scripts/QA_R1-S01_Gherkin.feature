Feature: R1-S01 - Authentication and User Management (US-01/US-02/US-03)
  As a QA agent
  I want to validate authentication and user lifecycle rules
  So that R1-S01 acceptance criteria are covered end-to-end

  Background:
    Given the API is reachable
    And an admin user exists with email "admin@rise.local" and password "Admin123!"

  Scenario: Login valido
    When I authenticate with email "admin@rise.local" and password "Admin123!"
    Then the response status should be 200
    And the response should contain a non-empty auth token

  Scenario: Login fallito per 5 tentativi blocca account per 15 minuti
    Given a user exists with email "locktest@rise.local" and password "Valid123!"
    When I authenticate 5 times with email "locktest@rise.local" and password "Wrong123!"
    Then each attempt should be rejected
    And the user account should be locked
    When I authenticate with email "locktest@rise.local" and password "Valid123!"
    Then the response status should indicate account locked
    And the lock duration should be 15 minutes

  Scenario: Login utente disattivato
    Given a user exists with email "disabled@rise.local" and password "Valid123!"
    And the user "disabled@rise.local" is deactivated
    When I authenticate with email "disabled@rise.local" and password "Valid123!"
    Then the response status should be 403
    And the error code should indicate user is deactivated

  Scenario: Create user OK
    Given I am authenticated as admin
    When I create a user with email "new.user@rise.local" role "USER" and valid profile data
    Then the response status should be 201
    And the created user should be persisted

  Scenario: Create user email duplicata
    Given I am authenticated as admin
    And a user already exists with email "duplicate@rise.local"
    When I create a user with email "duplicate@rise.local" role "USER" and valid profile data
    Then the response status should be 409
    And the error should indicate duplicate email

  Scenario: Create country manager senza countryScope (KO)
    Given I am authenticated as admin
    When I create a user with email "cm.noscope@rise.local" role "COUNTRY_MANAGER" without countryScope
    Then the response status should be 400
    And the error should indicate countryScope is required

  Scenario: Deactivate e reactivate utente OK
    Given I am authenticated as admin
    And a user exists with email "toggle.user@rise.local"
    When I deactivate user "toggle.user@rise.local"
    Then the response status should be 200
    And the user should be marked as deactivated
    When I reactivate user "toggle.user@rise.local"
    Then the response status should be 200
    And the user should be marked as active

  Scenario: Self-deactivate KO
    Given I am authenticated as user "self.user@rise.local"
    When I try to deactivate my own account
    Then the response status should be 403
    And the error should indicate self-deactivation is not allowed
