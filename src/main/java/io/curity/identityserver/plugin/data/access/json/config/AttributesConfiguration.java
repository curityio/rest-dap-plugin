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

package io.curity.identityserver.plugin.data.access.json.config;

import se.curity.identityserver.sdk.config.Configuration;
import se.curity.identityserver.sdk.config.OneOf;
import se.curity.identityserver.sdk.config.annotation.DefaultEnum;
import se.curity.identityserver.sdk.config.annotation.DefaultOption;
import se.curity.identityserver.sdk.config.annotation.DefaultString;
import se.curity.identityserver.sdk.config.annotation.Description;
import se.curity.identityserver.sdk.config.annotation.ListKey;

import java.util.List;
import java.util.Optional;

public interface AttributesConfiguration
{

    ParameterMappings parameterMappings();

    @Description("Configures how the subject is provided to the JSON service. "
            + "Defaults to substituting the subject in the url-path.")
    ProvideSubject provideSubject();

    interface ParameterMappings
    {
        @Description("Specifies a parameter name and how to get the value for it.")
        List<ParameterMappingConfiguration> parameterMapping();
    }

    interface ParameterMappingConfiguration extends Configuration
    {

        @ListKey
        @Description("The name of the parameter. The value of the authentication attribute " +
                "with the same name will be mapped.")
        String parameterName();

        @Description("Instead of using value of attribute named 'parameter-name' " +
                "you can configure how to get value.")
        Optional<Value> value();

        interface Value extends OneOf
        {
            @Description("The name of the attribute to get the value from. Will be fetched " +
                    "from the attributes available from the authentication. ")
            Optional<String> useValueOfAttribute();

            @Description("A static string to use as the value.")
            Optional<String> staticValue();
        }

    }

    interface ProvideSubject extends OneOf
    {

        @Description("The path relative to the webservice context, that makes up the subject's "
                + "attribute location that a GET-request will be made to. The path may contain the "
                + ":subject placeholder, where the username  is substituted. If it doesn't contain that "
                + "placeholder, use the username-parameter parameter to configure how the username is sent "
                + "over. Defaults to '/users/:subject'.")
        @DefaultOption
        Optional<@DefaultString("/users/:subject") String> urlPath();

        Optional<Parameter> parameter();

        interface Parameter
        {

            @Description("The path relative to the webservice context, that makes up the subject's "
                    + "attribute location that a GET-request will be made to. "
                    + "Defaults to '/users'.")
            @DefaultString("/users")
            String urlPath();

            @Description("Name of the parameter that will be used to provide the username to the "
                    + "remote service at the configured url-path.")
            String usernameParameter();

            @DefaultEnum("HEADER_PARAMETER")
            ProvideAs provideAs();

            @Description("Specify how the username is provided to the server. "
                    + "Defaults to header-parameter.")
            enum ProvideAs
            {
                @Description("Pass the username through the username-parameter in the querystring.")
                QUERY_PARAMETER,

                @Description("Pass the username through the username-parameter in the HTTP request "
                        + "header. The value of the username will be utf8/base64-encoded.")
                HEADER_PARAMETER
            }

        }

    }

}
