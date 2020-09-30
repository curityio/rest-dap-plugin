/*
 *  Copyright 2017 Curity AB
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.curity.identityserver.sdk.http.HttpResponse;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class WebUtils
{
    private static final Logger _logger = LoggerFactory.getLogger(WebUtils.class);

    private WebUtils()
    {
    }

    static String urlEncode(String value)
    {
        try
        {
            return URLEncoder.encode(value, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IllegalStateException("Cannot encode parameter, UTF-8 is not supported");
        }
    }

    static String urlEncodedFormData(Map<String, String> formParameters)
    {
        StringBuilder stringBuilder = new StringBuilder();

        boolean first = true;

        for (Map.Entry<String, String> entry : formParameters.entrySet())
        {
            if (!first)
            {
                stringBuilder.append("&");
            }
            else
            {
                first = false;
            }
            try
            {
                stringBuilder.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
            catch (UnsupportedEncodingException e)
            {
                throw new IllegalStateException("Unable to encode form data using UTF-8");
            }
        }

        return stringBuilder.toString();
    }

    static boolean isSuccessfulJsonResponse(HttpResponse response)
    {
        List<String> contentTypes = response.headers().allValues("Content-Type");

        if (contentTypes.isEmpty())
        {
            _logger.debug("JSON DataSource did not provide a Content-Type header, " +
                    "will attempt to parse the response as JSON.");
        }
        else
        {
            Optional<String> jsonContentType = contentTypes.stream().filter(WebUtils::isJson).findFirst();

            if (!jsonContentType.isPresent() && _logger.isDebugEnabled())
            {
                _logger.debug("JSON DataSource provided an unexpected Content-Type: '{}', " +
                        "will attempt to parse the response as JSON", String.join(", ", contentTypes));
            }
        }

        return hasSuccessStatusCode(response);
    }

    static boolean hasSuccessStatusCode(HttpResponse response)
    {
        return response.statusCode() >= 200
                && response.statusCode() < 300;
    }

    static boolean isJson(String contentType)
    {
        return Stream.of(contentType.split(","))
                .map(part -> Stream.of(part.split(";")).findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(String::trim)
                .collect(Collectors.toSet())
                .contains(JsonClientRequestContentType.APPLICATION_JSON.toString());

    }
}
