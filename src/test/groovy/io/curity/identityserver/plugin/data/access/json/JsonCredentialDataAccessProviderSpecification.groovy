/*
 * Copyright (C) 2017 Curity AB. All rights reserved.
 *
 * The contents of this file are the property of Curity AB.
 * You may not copy or use this file, in either source code
 * or executable form, except in compliance with terms
 * set by Curity AB.
 *
 * For further information, please contact Curity AB.
 */

package io.curity.identityserver.plugin.data.access.json

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.curity.identityserver.plugin.data.access.json.config.CredentialAccessConfiguration
import io.curity.identityserver.plugin.data.access.json.config.JsonDataAccessProviderConfiguration
import se.curity.identityserver.sdk.attribute.AccountAttributes
import se.curity.identityserver.sdk.http.HttpHeaders
import se.curity.identityserver.sdk.http.HttpRequest
import se.curity.identityserver.sdk.http.HttpResponse
import se.curity.identityserver.sdk.service.Json
import se.curity.identityserver.sdk.service.WebServiceClient
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

class JsonCredentialDataAccessProviderSpecification extends Specification {

    @Unroll
    'The correct request path is used depending on the configured username- and password parameters'() {
        given: 'Configuration for the JSON credential provider'
        def credentialAccessConfigurationMock = Stub(CredentialAccessConfiguration) {
                urlPath() >> path
        }

        and: 'A JSON DAP using mocked configuration'
        def jsonCredentialDAP = new JsonCredentialDataAccessProvider(Mock(JsonDataAccessProviderConfiguration) {
            getCredentialAccessConfiguration() >> credentialAccessConfigurationMock
        })

        when: 'the request path is created'
        def result = jsonCredentialDAP.createRequestPath(subject, password)

        then: 'the request was made to the correct endpoint'
        result == expectedPath

        where:
        path                            | subject     | password        || expectedPath
        '/'                             | 'foo'       | 'bar'           || '/'
        '/users/:subject'               | 'foo'       | 'bar'           || '/users/foo'
        '/users/:subject/:subject'      | 'foo'       | 'bar'           || '/users/foo/foo'
        '/:password/:password'          | 'foo'       | 'bar'           || '/bar/bar'
        '/:subject/bah'                 | 'foo'       | 'bar'           || '/foo/bah'
        '/users/:subject/:password'     | 'foo'       | 'bar'           || '/users/foo/bar'
        '/users/:subject/pwd/:password' | 'foo'       | 'bar'           || '/users/foo/pwd/bar'
        '/:password/:subject'           | 'foo'       | 'bar'           || '/bar/foo'

        '/:subject'                     | ':password' | 'bar'           || '/%3Apassword'
        '/:password'                    | 'foo'       | ':subject'      || '/%3Asubject'
        '/:subject/:password'           | ':password' | ':subject'      || '/%3Apassword/%3Asubject'
        '/:password/:subject'           | ':password' | ':subject'      || '/%3Asubject/%3Apassword'

        '/:subject?pwd=:password'       | '!"@#€%&/'  | '|\\()=?+'      || '/%21%22%40%23%E2%82%AC%25%26%2F?pwd=%7C%5C%28%29%3D%3F%2B'
    }

    def "The attributes are retrieved correctly from a JSON response provided by the JSON backend"() {
        given: 'a subject'
        def subject = 'johndoe'

        and: 'a JSON Response body as expected from a JSON backend'
        final now = Instant.now().epochSecond
        String validJsonResponseBody =
                """|
                |{
                |  "subject": "$subject",
                |  "password": "Password1",
                |  "now": $now,
                |  "static-list": ["squirtle", "wartortle", "blastoise"],
                |  "https://ws-fed.style.com/claim/name": "ash",
                |  "static-value": "final."
                |}""".stripMargin()

        and: 'a Mocked HTTP response which looks like the expected JSON response from the backend'
        def httpResponse = Stub(HttpResponse) {
            statusCode() >> 200
            body(_) >> validJsonResponseBody
            headers() >> Stub(HttpHeaders) {
                firstValue('Content-Type') >> 'application/json'
                map() >> ['Content-Type': ['application/json']]
            }
        }

        and: 'a JSON DAP using mocked configuration'
        def jsonCredentialDAP = new JsonCredentialDataAccessProvider(Mock(JsonDataAccessProviderConfiguration) {
            json() >> Stub(Json) {
                fromJson(validJsonResponseBody) >> new JsonSlurper().parseText(validJsonResponseBody)
            }
        })

        when:
        def actualAttributes = jsonCredentialDAP.getAuthenticationAttributesFrom(httpResponse, subject)

        then:
        actualAttributes != null
        actualAttributes.subject == subject

        def subjectAttributes = actualAttributes.subjectAttributes
        subjectAttributes.size() == 6
        subjectAttributes.get('subject')?.value == subject
        subjectAttributes['password']?.value == 'Password1'
        subjectAttributes['password'].name.format == 'json'
        subjectAttributes['now']?.value == now
        subjectAttributes['static-list']?.value == ["squirtle", "wartortle", "blastoise"]
        subjectAttributes['static-value']?.value == 'final.'
        subjectAttributes['https://ws-fed.style.com/claim/name'].value == 'ash'
        subjectAttributes['https://ws-fed.style.com/claim/name'].name.format == 'json'
    }

    def "The expected RESTful request is made when dap is asked to update password"() {
        def path = '/:subject?pwd=:password'
        def accountId = 'someid'
        def subject = 'johndoe'
        def password = 'Password1'
        def usernameParam = 'username'
        def passwordParam = 'password'

        given: 'Configuration for the JSON credential provider'
        def credentialAccessConfigurationMock = Stub(CredentialAccessConfiguration) {
            urlPath() >> path
            usernameParameter() >> usernameParam
            passwordParameter() >> passwordParam
        }

        and: 'a Mocked HTTP response which looks like the expected JSON response from the backend'
        def httpResponse = Stub(HttpResponse) {
            statusCode() >> 200
            body(_) >> "{}"
            headers() >> Stub(HttpHeaders) {
                firstValue('Content-Type') >> 'application/json'
                map() >> ['Content-Type': ['application/json']]
            }
        }

        and: 'a mocked web-service client'
        def mockedRequest = Mock(HttpRequest) {
            response() >> httpResponse
        }

        def mockedRequestBuilder = Mock(HttpRequest.Builder)
        mockedRequestBuilder._ >> mockedRequestBuilder

        def mockedClient = Mock(WebServiceClient) {
            request() >> mockedRequestBuilder
        }

        def mockedJson = Mock(Json)

        and: 'a JSON DAP using mocked configuration'
        def jsonCredentialDAP = new JsonCredentialDataAccessProvider(Mock(JsonDataAccessProviderConfiguration) {
            getCredentialAccessConfiguration() >> credentialAccessConfigurationMock
            webServiceClient() >> mockedClient
            json() >> mockedJson
        })

        when: 'asked to update the password'
        jsonCredentialDAP.updatePassword(AccountAttributes.of(accountId, subject).withPassword(password))

        then: 'the expected request was made'
        1 * mockedClient.withPath('/johndoe?pwd=Password1') >> mockedClient
        1 * mockedRequestBuilder.contentType('application/json') >> mockedRequestBuilder
        1 * mockedRequestBuilder.method('PUT') >> mockedRequest

        mockedJson.toJson(_) >> { Map attributes ->
            assert attributes[usernameParam] == subject
            assert attributes[passwordParam] == password
            new JsonOutput().toJson(_)
        }
    }

    @Unroll
    def "The expected RESTful request is made when dap is asked to verify password"() {
        def path = '/:subject'
        def subject = 'johndoe'
        def password = 'Password1'
        def usernameParam = 'username'
        def passwordParam = 'password'

        given: 'Configuration for the JSON credential provider'
        def credentialAccessConfigurationMock = Stub(CredentialAccessConfiguration) {
            urlPath() >> path
            usernameParameter() >> usernameParam
            passwordParameter() >> passwordParam
            submitAs() >> submissionType
            backendVerifiesPassword() >> verifyPassword

        }

        and: 'a JSON Response body as expected from a JSON backend'
        String validJsonResponseBody =
                """|
                |{
                |  "subject": "$subject",
                |  "password": "$password",
                |  "foo": "bar"
                |}""".stripMargin()

        and: 'a Mocked HTTP response which looks like the expected JSON response from the backend'
        def httpResponse = Stub(HttpResponse) {
            statusCode() >> 200
            body(_) >> validJsonResponseBody
            headers() >> Stub(HttpHeaders) {
                firstValue('Content-Type') >> 'application/json'
                map() >> ['Content-Type': ['application/json']]
            }
        }

        and: 'a mocked web-service client'
        def mockedRequest = Mock(HttpRequest) {
            response() >> httpResponse
        }

        def mockedRequestBuilder = Mock(HttpRequest.Builder)
        mockedRequestBuilder._ >> mockedRequestBuilder

        def mockedClient = Mock(WebServiceClient) {
            request() >> mockedRequestBuilder
        }

        def mockedJson = Mock(Json) {
            fromJson(_) >> { jsonBody -> new JsonSlurper().parseText(jsonBody) }
        }

        and: 'a JSON DAP using mocked configuration'
        def jsonCredentialDAP = new JsonCredentialDataAccessProvider(Mock(JsonDataAccessProviderConfiguration) {
            getCredentialAccessConfiguration() >> credentialAccessConfigurationMock
            webServiceClient() >> mockedClient
            json() >> mockedJson
        })

        when: 'asked to update the password'
        def authenticationAttributes = jsonCredentialDAP.verifyPassword(subject, password)

        then: 'the expected request was made'

        1 * mockedClient.withPath("/$subject") >> mockedClient
        1 * mockedRequestBuilder.accept('application/json') >> mockedRequestBuilder
        (0..1) * mockedRequestBuilder.contentType(expectedContentType) >> mockedRequestBuilder

        1 * mockedRequestBuilder.method(_) >> { String method ->
            if (submissionType == CredentialAccessConfiguration.SubmitAs.GET_AS_QUERYSTRING) {
                assert method == 'GET'
            } else {
                assert method == 'POST'
            }
            mockedRequest
        }

        // Used when submissionType == GET_AS_QUERYSTRING
        mockedClient.withQueries(_) >> { Map<String, Collection<String>> queryParameters ->
            assert queryParameters[usernameParam].size() == 1
            assert queryParameters[usernameParam][0] == subject
            if (verifyPassword) {
                assert queryParameters[passwordParam].size() == 1
                assert queryParameters[passwordParam][0] == password
            }
            mockedClient
        }

        // Used when submissionType == POST_AS_JSON
        mockedJson.toJson(_) >> { Map attributes ->
            assert attributes[usernameParam] == subject
            if (verifyPassword) {
                assert attributes[passwordParam] == password
            }
            new JsonOutput().toJson(_)
        }

        // TODO: Also verify request body for content-type 'application/x-www-form-urlencoded'

        and: 'the returned authentication attributes looks as expected'
        authenticationAttributes.subjectAttributes.subject == subject
        authenticationAttributes.subjectAttributes['password']?.value == 'Password1'
        authenticationAttributes.subjectAttributes['foo']?.value == 'bar'

        where:
        verifyPassword | submissionType                                                     || expectedContentType
        true           | CredentialAccessConfiguration.SubmitAs.POST_AS_JSON                || 'application/json'
        false          | CredentialAccessConfiguration.SubmitAs.POST_AS_JSON                || 'application/json'
        true           | CredentialAccessConfiguration.SubmitAs.POST_AS_URLENCODED_FORMDATA || 'application/x-www-form-urlencoded'
        false          | CredentialAccessConfiguration.SubmitAs.POST_AS_URLENCODED_FORMDATA || 'application/x-www-form-urlencoded'
        true           | CredentialAccessConfiguration.SubmitAs.GET_AS_QUERYSTRING          || null
        false          | CredentialAccessConfiguration.SubmitAs.GET_AS_QUERYSTRING          || null
    }
}
