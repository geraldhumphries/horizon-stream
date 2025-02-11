Feature: Minion Basic Functionality

  Background: Configure base URLs
    Given MOCK Minion Gateway Base URL in system property "mock-miniongateway.base-url"
    Given Application Base URL in system property "application.base-url"

  Scenario: Verify on startup the Minion has no tasks deployed
    Then Send GET request to application at path "/ignite-worker/service-deployment/metrics?verbose=true" until success with timeout 60000ms
    Then DEBUG dump the response body
    Then parse the JSON response
    Then verify JSON path expressions match
      | total == 0 |
      | serviceCount == 0 |

  Scenario: Wait for Minion to register with the Mock Gateway
    Then MOCK wait for minion connection with id "test-minion-001" and location "Default" timeout 30000ms

  Scenario: Add a task to the Minion (via the Minion Gateway) and verify the task is deployed
    Then Send GET request to application at path "/ignite-worker/service-deployment/metrics?verbose=true" until success with timeout 60000ms
    Then Remember response body for later comparison

    Given MOCK twin update in resource file "/testdata/task-set.twin.001.json"
    Then MOCK send twin update for topic "task-set" at location "Default"

    Then Send GET request to application at path "/ignite-worker/service-deployment/metrics?verbose=true" until response changes with timeout 60000ms

    Then DEBUG dump the response body
    Then parse the JSON response
    Then verify JSON path expressions match
      | serviceCount == 1 |

  Scenario: Add another task to the Minion (via the Minion Gateway) and verify the task is deployed
    Then Send GET request to application at path "/ignite-worker/service-deployment/metrics?verbose=true"
    Then Remember response body for later comparison

    Given MOCK twin update in resource file "/testdata/task-set.twin.002.json"
    Then MOCK send twin update for topic "task-set" at location "Default"

    Then Send GET request to application at path "/ignite-worker/service-deployment/metrics?verbose=true" until response changes with timeout 60000ms
    Then DEBUG dump the response body
    Then parse the JSON response
    Then verify JSON path expressions match
      | serviceCount == 2 |
