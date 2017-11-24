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

import se.curity.identityserver.sdk.config.annotation.DefaultBoolean;
import se.curity.identityserver.sdk.config.annotation.DefaultEnum;
import se.curity.identityserver.sdk.config.annotation.DefaultString;
import se.curity.identityserver.sdk.config.annotation.Description;

public interface CredentialAccessConfiguration
{

    @Description("If set to true, the backend will verify the password. It is required the server "
            + "responds with HTTP Success to indicate a successful password verification."
            + "If set to false the password will not be sent to the server and the response should "
            + "contain both the username, password and the status of the account.")
    @DefaultBoolean(false)
    boolean backendVerifiesPassword();

    @Description("Specify how username and password are provided to the server. This sets both "
            + "the HTTP method that is used, as well as the content-type that the data is encoded with")
    @DefaultEnum("POST_AS_JSON")
    SubmitAs submitAs();

    @Description("Name of the parameter that will contain the username in a query.")
    @DefaultString("username")
    String usernameParameter();

    @Description("Name of the parameter that will contain the password in a query.")
    @DefaultString("password")
    String passwordParameter();

    @Description("The path relative to the webservice context to make the request to. "
            + "The path may contain the :subject and :password placeholders, which are substituted with "
            + "username and password, respectively.")
    @DefaultString("/")
    String urlPath();

    enum SubmitAs
    {
        @Description("POST the data and encode the data using 'application/json' content-type")
        POST_AS_JSON,

        @Description("POST the data and encode the data using 'application/x-www-form-urlencoded' ")
        POST_AS_URLENCODED_FORMDATA,

        @Description("GET the data and encode the data in an URL-encoded query-string")
        GET_AS_QUERYSTRING
    }

}
