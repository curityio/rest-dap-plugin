/*
 *  Copyright 2015 Curity AB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.curity.identityserver.plugin.data.access.json;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import io.curity.identityserver.plugin.data.access.json.config.CredentialAccessConfiguration;
import io.curity.identityserver.plugin.data.access.json.config.JsonDataAccessProviderConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.curity.identityserver.sdk.Nullable;
import se.curity.identityserver.sdk.attribute.AccountAttributes;
import se.curity.identityserver.sdk.attribute.AttributeName;
import se.curity.identityserver.sdk.attribute.Attributes;
import se.curity.identityserver.sdk.attribute.AuthenticationAttributes;
import se.curity.identityserver.sdk.attribute.ContextAttributes;
import se.curity.identityserver.sdk.attribute.SubjectAttributes;
import se.curity.identityserver.sdk.datasource.CredentialDataAccessProvider;
import se.curity.identityserver.sdk.http.HttpRequest;
import se.curity.identityserver.sdk.http.HttpResponse;
import se.curity.identityserver.sdk.service.Json;
import se.curity.identityserver.sdk.service.WebServiceClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static io.curity.identityserver.plugin.data.access.json.CollectionUtils.toMultiMap;
import static io.curity.identityserver.plugin.data.access.json.WebUtils.isSuccessfulJsonResponse;
import static io.curity.identityserver.plugin.data.access.json.WebUtils.urlEncode;
import static io.curity.identityserver.plugin.data.access.json.WebUtils.urlEncodedFormData;

public class JsonCredentialDataAccessProvider implements CredentialDataAccessProvider
{
    private static final String SUBJECT_PLACEHOLDER = ":subject";
    private static final String PASSWORD_PLACEHOLDER = ":password";
    private static final Logger _logger = LoggerFactory.getLogger(JsonCredentialDataAccessProvider.class);

    private final CredentialAccessConfiguration _configuration;
    private final Json _json;
    private final WebServiceClient _webServiceClient;

    @SuppressWarnings("unused") // used through DI
    public JsonCredentialDataAccessProvider(JsonDataAccessProviderConfiguration configuration)
    {
        _configuration = configuration.getCredentialAccessConfiguration();
        _json = configuration.json();
        _webServiceClient = configuration.webServiceClient();
    }

    @Override
    public void updatePassword(AccountAttributes account)
    {
        String subjectId = account.getUserName();
        Optional<String> newPassword = Optional.ofNullable(account.getPassword());

        if (!newPassword.isPresent())
        {
            _logger.warn("Cannot update account password, missing password value");
            return;
        }

        String requestPath = createRequestPath(subjectId, newPassword.get());
        Map<String, String> requestParameterMap = createRequestParameterMap(subjectId, newPassword.get());

        // updatePassword must use HTTP PUT.
        HttpResponse jsonResponse = _webServiceClient
                .withPath(requestPath)
                .request()
                .accept(JsonClientRequestContentType.APPLICATION_JSON.toString())
                .contentType(JsonClientRequestContentType.APPLICATION_JSON.toString())
                .body(HttpRequest.fromString(_json.toJson(requestParameterMap), StandardCharsets.UTF_8))
                .method("PUT")
                .response();

        if (isSuccessfulJsonResponse(jsonResponse))
        {
            _logger.debug("The update password request for {} reported success.", subjectId);
        }
        else
        {
            _logger.info("The update password request for {} reported failure (HTTP response {})",
                    subjectId, jsonResponse.statusCode());

            String responseBody = jsonResponse.body(HttpResponse.asString());

            if (!responseBody.isEmpty())
            {
                _logger.trace("Message returned in response body:\n{}", responseBody);
            }
            else
            {
                _logger.trace("No message returned in response body.");
            }
        }
    }

    @Override
    @Nullable
    public AuthenticationAttributes verifyPassword(String userName, String password)
    {
        String requestPath = createRequestPath(userName, password);
        Map<String, String> requestParameterMap;

        if (_configuration.backendVerifiesPassword())
        {
            requestParameterMap = createRequestParameterMap(userName, password);
        }
        else
        {
            // Don't send the password when the backend is not doing anything with it
            requestParameterMap = createRequestParameterMap(userName, null);
        }

        WebServiceClient webServiceClient = _webServiceClient.withPath(requestPath);

        HttpRequest request = getHttpRequestToVerifyPassword(requestParameterMap, webServiceClient);

        HttpResponse jsonResponse = request.response();

        _logger.debug("JSON data-source responds with status: {}", jsonResponse.statusCode());

        return getAuthenticationAttributesFrom(jsonResponse, userName);
    }

    @VisibleForTesting
    @Nullable
    AuthenticationAttributes getAuthenticationAttributesFrom(HttpResponse jsonResponse, String userName)
    {
        @Nullable AuthenticationAttributes attributes = null;

        String responseBody = jsonResponse.body(HttpResponse.asString());

        boolean isHttpSuccessResponse = isSuccessfulJsonResponse(jsonResponse);

        if (!isHttpSuccessResponse)
        {
            // Debug level logging, as the response is not reporting OK/success
            if (!responseBody.isEmpty())
            {
                _logger.debug("Response from JSON data-source:\n{}", responseBody);
            }
            else
            {
                _logger.debug("No response body from JSON data-source.");
            }
        }
        else if (responseBody.isEmpty())
        {
            _logger.warn("Received JSON response without response body. The JSON server answer is inconsistent?");
        }
        else
        {
            _logger.trace("Processing JSON response from successful response");

            // Let all the returned JSON-attributes be categorized as subject-attributes
            attributes = AuthenticationAttributes.of(
                    SubjectAttributes.of(userName, readFromJsonResponse(responseBody)),
                    ContextAttributes.empty());
        }

        return attributes;
    }

    private HttpRequest getHttpRequestToVerifyPassword(Map<String, String> requestParameterMap,
                                                       WebServiceClient webServiceClient)
    {
        switch (_configuration.submitAs())
        {
            case POST_AS_JSON:
                return webServiceClient.request()
                        .contentType(JsonClientRequestContentType.APPLICATION_JSON.toString())
                        .accept(JsonClientRequestContentType.APPLICATION_JSON.toString())
                        .body(HttpRequest.fromString(_json.toJson(requestParameterMap), StandardCharsets.UTF_8))
                        .method("POST");
            case POST_AS_URLENCODED_FORMDATA:
                return webServiceClient.request()
                        .contentType(JsonClientRequestContentType.APPLICATION_WWW_FORM_URLENCODED.toString())
                        .accept(JsonClientRequestContentType.APPLICATION_JSON.toString())
                        .body(HttpRequest.fromString(urlEncodedFormData(requestParameterMap), StandardCharsets.ISO_8859_1))
                        .method("POST");
            case GET_AS_QUERYSTRING:
                return webServiceClient.withQueries(toMultiMap(requestParameterMap)).request()
                        .accept(JsonClientRequestContentType.APPLICATION_JSON.toString())
                        .method("GET");
            default:
                throw new IllegalStateException("unknown value for submit-as: " + _configuration.submitAs());
        }
    }

    private Attributes readFromJsonResponse(String responseBody)
    {
        return Attributes.fromMap(_json.fromJson(responseBody), AttributeName.Format.JSON);
    }

    @Override
    public boolean customQueryVerifiesPassword()
    {
        return _configuration.backendVerifiesPassword();
    }

    /**
     * Helper method that crafts the request path that the call is made to. Can consider
     * the username and password to substitute pars of the path if needed.
     */
    @VisibleForTesting
    String createRequestPath(String subject, String password)
    {
        return _configuration.urlPath()
                .replaceAll(SUBJECT_PLACEHOLDER, urlEncode(subject))
                .replaceAll(PASSWORD_PLACEHOLDER, urlEncode(password));
    }

    private Map<String, String> createRequestParameterMap(String subjectId, @Nullable String password)
    {
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<String, String>()
                .put(_configuration.usernameParameter(), subjectId);

        if (password != null)
        {
            builder.put(_configuration.passwordParameter(), password);
        }

        return builder.build();
    }

}