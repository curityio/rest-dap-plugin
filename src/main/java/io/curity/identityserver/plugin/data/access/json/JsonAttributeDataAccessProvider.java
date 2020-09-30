/*
 *  Copyright 2016 Curity AB
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
import io.curity.identityserver.plugin.data.access.json.config.AttributesConfiguration;
import io.curity.identityserver.plugin.data.access.json.config.AttributesConfiguration.ProvideSubject.Parameter;
import io.curity.identityserver.plugin.data.access.json.config.JsonDataAccessProviderConfiguration;
import io.curity.identityserver.plugin.data.access.json.parameter.AttributeLookupMapping;
import io.curity.identityserver.plugin.data.access.json.parameter.ParameterMapping;
import io.curity.identityserver.plugin.data.access.json.parameter.StaticMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.curity.identityserver.sdk.Nullable;
import se.curity.identityserver.sdk.ThreadSafe;
import se.curity.identityserver.sdk.attribute.AttributeName;
import se.curity.identityserver.sdk.attribute.AttributeTableView;
import se.curity.identityserver.sdk.attribute.Attributes;
import se.curity.identityserver.sdk.attribute.SubjectAttributes;
import se.curity.identityserver.sdk.datasource.AttributeDataAccessProvider;
import se.curity.identityserver.sdk.http.HttpResponse;
import se.curity.identityserver.sdk.service.Json;
import se.curity.identityserver.sdk.service.WebServiceClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.curity.identityserver.plugin.data.access.json.CollectionUtils.toArray;
import static io.curity.identityserver.plugin.data.access.json.CollectionUtils.toMultiMap;
import static se.curity.identityserver.sdk.http.HttpResponse.asString;

public class JsonAttributeDataAccessProvider implements AttributeDataAccessProvider, ThreadSafe
{
    private static final String SUBJECT_PLACEHOLDER = ":subject";

    private static final Logger _logger = LoggerFactory.getLogger(JsonAttributeDataAccessProvider.class);

    private final AttributesConfiguration _configuration;
    private final WebServiceClient _webServiceClient;
    private final Json _json;

    @SuppressWarnings("unused") // used through DI
    public JsonAttributeDataAccessProvider(JsonDataAccessProviderConfiguration configuration)
    {
        _configuration = configuration.getAttributesConfiguration();
        _json = configuration.json();
        _webServiceClient = configuration.webServiceClient();

        _logger.trace("Instantiating Json Attributes data-source plugin with ID={}", configuration.id());
    }

    @Override
    public AttributeTableView getAttributes(String subject)
    {
        SubjectAttributes subjectAttributes = SubjectAttributes.of(subject, Attributes.empty());
        return getAttributes(subjectAttributes);
    }

    @Override
    public AttributeTableView getAttributes(SubjectAttributes subjectAttributes)
    {
        AttributeTableView result = AttributeTableView.empty();

        String requestPath = createRequestPath(subjectAttributes.getSubject());
        Map<String, String> queryParameters = createQueryParameters(subjectAttributes);
        Map<String, String> headerParameters = createHeaderParameters(subjectAttributes);

        HttpResponse response = _webServiceClient
                .withQueries(toMultiMap(queryParameters))
                .withPath(requestPath)
                .request()
                .header(toArray(headerParameters))
                .accept(JsonClientRequestContentType.APPLICATION_JSON.toString())
                .method("GET")
                .response();

        @Nullable Attributes attributes = getAttributesFrom(response);

        if (attributes != null)
        {
            result = AttributeTableView.of(Collections.singletonList(attributes.asMap()));
        }

        return result;

    }

    @VisibleForTesting
    @Nullable
    Attributes getAttributesFrom(HttpResponse jsonResponse)
    {
        @Nullable Attributes responseAttributes = null;

        String responseBody = jsonResponse.body(asString());

        if (!WebUtils.isSuccessfulJsonResponse(jsonResponse))
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

            try
            {
                responseAttributes = Attributes.fromMap(_json.fromJson(responseBody), AttributeName.Format.JSON);
            }
            catch (Json.JsonException e)
            {
                _logger.warn("Could not parse JSON response from server due to '{}': {}", e.getMessage(), responseBody);
            }
        }

        return responseAttributes;
    }

    @VisibleForTesting
    String createRequestPath(String subject)
    {
        return _configuration.provideSubject().urlPath()
                // if the choice is to use a urlPath, use it by replacing :subject with the subject value
                .map(path -> path.replaceAll(SUBJECT_PLACEHOLDER, WebUtils.urlEncode(subject)))
                // if the choice is to use a parameter to provide the subject, use the urlPath as-is.
                .orElseGet(() -> _configuration.provideSubject().parameter()
                        .map(Parameter::urlPath)
                        .orElseThrow(() -> new IllegalStateException("One-of was not set to any value")));
    }

    @VisibleForTesting
    Map<String, String> createQueryParameters(SubjectAttributes subjectAttributes)
    {
        return createParameters(subjectAttributes, Parameter.ProvideAs.QUERY_PARAMETER);
    }

    @VisibleForTesting
    Map<String, String> createHeaderParameters(SubjectAttributes subjectAttributes)
    {
        return createParameters(subjectAttributes, Parameter.ProvideAs.HEADER_PARAMETER);
    }

    private Map<String, String> createParameters(SubjectAttributes subjectAttributes, Parameter.ProvideAs provideAs)
    {
        if (!_configuration.provideSubject().parameter().isPresent())
        {
            // not configured to use parameters
            return Collections.emptyMap();
        }

        Parameter parameterConfig = _configuration.provideSubject().parameter().get();

        if (!parameterConfig.provideAs().equals(provideAs))
        {
            // not configured to use requested parameter type
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();

        String subject = subjectAttributes.getSubject();
        String encodedSubject = encodeFunctionFor(provideAs).apply(subject);
        result.put(parameterConfig.usernameParameter(), encodedSubject);

        List<ParameterMapping> mappings = _configuration.parameterMappings().parameterMapping().stream()
                .map(JsonAttributeDataAccessProvider::parameterMapping)
                .collect(Collectors.toList());

        for (ParameterMapping mapping : mappings)
        {
            @Nullable String mappedValue = mapping.getMappedValue(subjectAttributes);

            if (mappedValue != null)
            {
                result.put(mapping.getParameterName(), encodeFunctionFor(provideAs).apply(mappedValue));
            }
        }

        return result;
    }

    private static Function<String, String> encodeFunctionFor(Parameter.ProvideAs provideAs)
    {
        switch (provideAs)
        {
            case HEADER_PARAMETER:
                return (String value) -> Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            case QUERY_PARAMETER:
                // Possible URL-encoding is not the JSON DAP's, but the WebServiceClient's responsibility
                return Function.identity();
            default:
                throw new IllegalArgumentException("Unknown ProvideAs instance: " + provideAs);
        }
    }

    private static ParameterMapping parameterMapping(AttributesConfiguration.ParameterMappingConfiguration mappingConfig)
    {
        Optional<AttributesConfiguration.ParameterMappingConfiguration.Value> optionalValue = mappingConfig.value();

        if (!optionalValue.isPresent())
        {
            // use the value of the attribute unchanged
            return new AttributeLookupMapping(mappingConfig.parameterName(), mappingConfig.parameterName());
        }

        AttributesConfiguration.ParameterMappingConfiguration.Value value = optionalValue.get();

        if (value.useValueOfAttribute().isPresent())
        {
            return new AttributeLookupMapping(mappingConfig.parameterName(),
                    value.useValueOfAttribute().get());
        }

        if (value.staticValue().isPresent())
        {
            return new StaticMapping(mappingConfig.parameterName(), value.staticValue().get());
        }

        throw new IllegalStateException("One-of ParameterMappingConfiguration did not have any value set");
    }
}
