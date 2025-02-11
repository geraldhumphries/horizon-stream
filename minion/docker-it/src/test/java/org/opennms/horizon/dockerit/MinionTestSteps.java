/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012-2021 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2021 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 ******************************************************************************/

package org.opennms.horizon.dockerit;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.opennms.horizon.testtool.miniongateway.wiremock.client.MinionGatewayWiremockTestSteps;
import org.opennms.horizon.testtool.miniongateway.wiremock.client.RetryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MinionTestSteps {

    public static final int DEFAULT_HTTP_SOCKET_TIMEOUT = 15_000;

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(MinionTestSteps.class);

    private Logger log = DEFAULT_LOGGER;

    private MinionGatewayWiremockTestSteps minionGatewayWiremockTestSteps;
    private RetryUtils retryUtils; // TODO: this belongs in a better shared place

    //
    // Test Configuration
    //
    private String applicationBaseUrl;

    //
    // Test Runtime Data
    //
    private Response restAssuredResponse;
    private Response rememberedRestAssuredResponse;
    private JsonPath parsedJsonResponse;

//========================================
// Constructor
//========================================

    public MinionTestSteps(MinionGatewayWiremockTestSteps minionGatewayWiremockTestSteps, RetryUtils retryUtils) {
        this.minionGatewayWiremockTestSteps = minionGatewayWiremockTestSteps;
        this.retryUtils = retryUtils;
    }


//========================================
// Gherkin Rules
//========================================

    @Given("Application Base URL in system property {string}")
    public void applicationBaseURLInSystemProperty(String systemProperty) {
        applicationBaseUrl = System.getProperty(systemProperty);

        log.info("Using BASE URL {}", applicationBaseUrl);
    }

    @Then("Send GET request to application at path {string}")
    public void sendGETRequestToApplicationAtPath(String path) throws Exception {
        commonSendGetRequestToApplication(path);
    }

    @Then("Send GET request to application at path {string} until success with timeout {int}ms")
    public void sendGETRequestToApplicationAtPathUntilSuccessWithTimeoutMs(String path, int timeout) throws InterruptedException {
        retryUtils.retry(
            () -> retryableSendGetRequestToApplication(path),
            (response) -> ( response != null ) && isSuccessHttpStatusCode(response.getStatusCode()),
            100,
            timeout,
            null
        );
    }

    @Then("Remember response body for later comparison")
    public void rememberResponseBodyForLaterComparison() {
        rememberedRestAssuredResponse = restAssuredResponse;
    }

    @Then("Send GET request to application at path {string} until response changes with timeout {int}ms")
    public void sendGETRequestToApplicationAtPathUntilResponseChangesWithTimeoutMs(String path, int timeoutMs) throws InterruptedException {
        retryUtils.retry(
            () -> retryableSendGetRequestToApplication(path),
            (newResponse) -> checkResponseChanged((Response) newResponse),
            5,
            timeoutMs,
            false
        );
    }

    @Then("^parse the JSON response$")
    public void parseTheJsonResponse() {
        parsedJsonResponse = JsonPath.from((this.restAssuredResponse.getBody().asString()));
    }

    @Then("^verify JSON path expressions match$")
    public void verifyJsonPathExpressionsMatch(List<String> pathExpressions) {
        for (String onePathExpression : pathExpressions) {
            verifyJsonPathExpressionMatch(parsedJsonResponse, onePathExpression);
        }
    }

//========================================
// Utility Rules
//----------------------------------------

    @Then("^DEBUG dump the response body$")
    public void debugDumpTheResponseBody() {
        this.log.info("RESPONSE BODY = {}", restAssuredResponse.getBody().asString());
    }

    @Then("delay {int}ms")
    public void delayMs(int ms) throws Exception {
        Thread.sleep(ms);
    }

//========================================
// Internals
//----------------------------------------

    private boolean isSuccessHttpStatusCode(int code) {
        return ( ( (code) >= 200 ) && ( code <= 299 ) );
    }

    private RestAssuredConfig createRestAssuredTestConfig() {
        return RestAssuredConfig.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", DEFAULT_HTTP_SOCKET_TIMEOUT)
                .setParam("http.socket.timeout", DEFAULT_HTTP_SOCKET_TIMEOUT)
            );
    }

    private void verifyJsonPathExpressionMatch(JsonPath jsonPath, String pathExpression) {
        String[] parts = pathExpression.split(" == ", 2);

        if (parts.length == 2) {
            // Expression and value to match - evaluate as a string and compare
            String actualValue = jsonPath.getString(parts[0]);
            String actualTrimmed;

            if (actualValue != null) {
                actualTrimmed = actualValue.trim();
            }  else {
                actualTrimmed = null;
            }

            String expectedTrimmed = parts[1].trim();

            assertEquals("matching to JSON path " + parts[0], expectedTrimmed, actualTrimmed);
        } else {
            // Just an expression - evaluate as a boolean
            assertTrue("verifying JSON path expression " + pathExpression, jsonPath.getBoolean(pathExpression));
        }
    }

    private Response retryableSendGetRequestToApplication(String path) {
        try {
            commonSendGetRequestToApplication(path);
        } catch (MalformedURLException muExc) {
            throw new RuntimeException(muExc);
        }

        return restAssuredResponse;
    }

    private void commonSendGetRequestToApplication(String path) throws MalformedURLException {
        URL requestUrl = new URL(new URL(this.applicationBaseUrl), path);

        RestAssuredConfig restAssuredConfig = this.createRestAssuredTestConfig();

        RequestSpecification requestSpecification =
            RestAssured
                .given()
                .config(restAssuredConfig);

        restAssuredResponse =
            requestSpecification
                .get(requestUrl)
                .thenReturn()
        ;
    }

    private boolean checkResponseChanged(Response newResponse) {
        if (rememberedRestAssuredResponse == null) {
            if (newResponse == null) {
                return false;
            } else {
                return true;
            }
        }

        if (newResponse == null) {
            return true;
        }

        if (newResponse.getStatusCode() != rememberedRestAssuredResponse.getStatusCode()) {
            log.info("STATUS CODE CHANGE: {} -> {}", rememberedRestAssuredResponse.getStatusCode(), newResponse.getStatusCode());
            return true;
        }

        if (! Objects.equals(newResponse.getBody().asString(), rememberedRestAssuredResponse.getBody().asString())) {
            log.info("BODY CHANGE: {} -> {}", rememberedRestAssuredResponse.getBody().asString(), newResponse.getBody().asString());
            return true;
        }

        return false;
    }
}
