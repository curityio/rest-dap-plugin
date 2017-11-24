/*
 * Copyright (C) 2016 Curity AB. All rights reserved.
 *
 * The contents of this file are the property of Curity AB.
 * You may not copy or use this file, in either source code
 * or executable form, except in compliance with terms
 * set by Curity AB.
 *
 * For further information, please contact Curity AB.
 */

package io.curity.identityserver.plugin.data.access.json

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.curity.identityserver.plugin.data.access.json.config.AttributesConfiguration
import io.curity.identityserver.plugin.data.access.json.config.JsonDataAccessProviderConfiguration
import se.curity.identityserver.sdk.attribute.SubjectAttributes
import se.curity.identityserver.sdk.http.HttpHeaders
import se.curity.identityserver.sdk.http.HttpResponse
import se.curity.identityserver.sdk.service.Json
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant

import static io.curity.identityserver.plugin.data.access.json.config.AttributesConfiguration.ProvideSubject.Parameter.ProvideAs.HEADER_PARAMETER
import static io.curity.identityserver.plugin.data.access.json.config.AttributesConfiguration.ProvideSubject.Parameter.ProvideAs.QUERY_PARAMETER

class JsonAttributeDataAccessProviderSpecification extends Specification {

    @Unroll
    'The correct request path is used depending on the configured username-parameter'() {
        given: 'Configuration for the JSON Attribute provider without username-parameter'
        def attributesConfigurationMock = Stub(AttributesConfiguration) {
            provideSubject() >> Stub(AttributesConfiguration.ProvideSubject) {
                urlPath() >> urlPathOptional
                parameter() >> Optional.of(Stub(AttributesConfiguration.ProvideSubject.Parameter) {
                    urlPath() >> parameterUrlPath
                })
            }
        }

        and: 'A JSON DAP using mocked configuration'
        def jsonAttributeDAP = new JsonAttributeDataAccessProvider(Mock(JsonDataAccessProviderConfiguration) {
            getAttributesConfiguration() >> attributesConfigurationMock
        })

        when: 'the request path is created'
        def result = jsonAttributeDAP.createRequestPath(subject)

        then: 'the request was made to the correct endpoint'
        result == expectedPath

        where:
        urlPathOptional                   | parameterUrlPath   | subject            || expectedPath
        Optional.of('/users/:subject')    | ''                 | 'foo'              || '/users/foo'
        Optional.of('/:subject/:subject') | ''                 | 'foo'              || '/foo/foo'
        Optional.of('/:subject/bah')      | ''                 | 'foo'              || '/foo/bah'
        Optional.empty()                  | '/users/all'       | 'foo'              || '/users/all'

        Optional.of('/:subject')          | ''                 | '!"@#€%&/|\\()=?+' || '/%21%22%40%23%E2%82%AC%25%26%2F%7C%5C%28%29%3D%3F%2B'
    }

    @Unroll
    @Issue("IS-2237")
    'The correct query parameters are provided when provideSubject is configured to use query parameters'() {
        given: 'Configuration for the JSON Attribute provider without username-parameter'
        def attributesConfigurationMock = Stub(AttributesConfiguration) {
            parameterMappings() >> asParameterMappings(configParameterMappings)
            provideSubject() >> Stub(AttributesConfiguration.ProvideSubject) {
                urlPath() >> Optional.empty()
                parameter() >> Optional.of(Stub(AttributesConfiguration.ProvideSubject.Parameter) {
                    urlPath() >> 'xxxx'
                    usernameParameter() >> 'sub'
                    provideAs() >> QUERY_PARAMETER
                })
            }
        }

        and: 'A JSON DAP using mocked configuration'
        def jsonAttributeDAP = new JsonAttributeDataAccessProvider(Mock(JsonDataAccessProviderConfiguration) {
            getAttributesConfiguration() >> attributesConfigurationMock
        })

        when: 'the query parameters are created for the request'
        SubjectAttributes subjectAttributes = subjectOf(subjectAttributesMap)
        def actualQueryParameters = jsonAttributeDAP.createQueryParameters(subjectAttributes)

        and: 'the header parameters are created for the request'
        def actualHeaderParameters = jsonAttributeDAP.createHeaderParameters(subjectAttributes)

        then: 'the query parameters contain the expected, un-encoded entries'
        actualQueryParameters == queryMap

        and: 'the header parameters are empty'
        actualHeaderParameters.isEmpty()

        where:
        configParameterMappings                               | subjectAttributesMap            | queryMap
        [[name: 'username', useValueOfAttribute: 'a']]        | [a: 'hello', subject: 'doe']    | [sub: 'doe', 'username': 'hello']
        [[name: 'subject', staticValue: 'bruno']]             | [a: 'hello', subject: 'doe']    | [sub: 'doe', 'subject': 'bruno']
        [[name: 'subject', useValueOfAttribute: 'not_there']] | [a: 'hello', subject: 'doe']    | [sub: 'doe']
        [[name: 'name', staticValue: 'bruno']]                | [a: 'hello', subject: 'doe']    | [sub: 'doe', 'name': 'bruno']
        [[name: 'username', useValueOfAttribute: 'a']]        | [a: 'björn öl', subject: 'doe'] | [sub: 'doe', 'username': 'björn öl']
        [[name: 'subject', staticValue: 'brüno']]             | [a: 'hello', subject: 'doe']    | [sub: 'doe', 'subject': 'brüno']
        [[name: 'subject', staticValue: 'brüno'],
         [name: 'username', useValueOfAttribute: 'a']]        | [a: 'hello', subject: 'doe']    | [sub     : 'doe',
                                                                                                   subject : 'brüno',
                                                                                                   username: 'hello']

    }

    @CompileStatic
    // this is needed to Groovy does not call the wrong method by dispatching dynamically
    private static SubjectAttributes subjectOf(Map map) {
        SubjectAttributes.of(map)
    }

    private AttributesConfiguration.ParameterMappings asParameterMappings(List<Map> mappings) {
        Stub(AttributesConfiguration.ParameterMappings) {
            parameterMapping() >> mappings.collect { asParameterMapping(it) }
        }
    }

    private AttributesConfiguration.ParameterMappingConfiguration asParameterMapping(Map config) {
        def configValue = config.useValueOfAttribute ?
                Optional.of(Stub(AttributesConfiguration.ParameterMappingConfiguration.Value) {
                    useValueOfAttribute() >> Optional.of(config.useValueOfAttribute as String)
                    staticValue() >> Optional.empty()
                }) :
                Optional.of(Stub(AttributesConfiguration.ParameterMappingConfiguration.Value) {
                    useValueOfAttribute() >> Optional.empty()
                    staticValue() >> Optional.of(config.staticValue as String)
                })

        Stub(AttributesConfiguration.ParameterMappingConfiguration) {
            parameterName() >> config.name
            value() >> configValue
        }
    }

    @Unroll
    'The correct header parameters are provided when provideSubject is configured to use header parameters'() {
        given: 'Configuration for the JSON Attribute provider without username-parameter'
        def attributesConfigurationMock = Stub(AttributesConfiguration) {
            parameterMappings() >> asParameterMappings(configParameterMappings)
            provideSubject() >> Stub(AttributesConfiguration.ProvideSubject) {
                urlPath() >> Optional.empty()
                parameter() >> Optional.of(Stub(AttributesConfiguration.ProvideSubject.Parameter) {
                    urlPath() >> 'xxxx'
                    usernameParameter() >> 'sub'
                    provideAs() >> HEADER_PARAMETER
                })
            }
        }

        and: 'A JSON DAP using mocked configuration'
        def jsonAttributeDAP = new JsonAttributeDataAccessProvider(Mock(JsonDataAccessProviderConfiguration) {
            getAttributesConfiguration() >> attributesConfigurationMock
        })

        when: 'the query parameters are created for the request'
        SubjectAttributes subjectAttributes = subjectOf(subjectAttributesMap)
        def actualQueryParameters = jsonAttributeDAP.createQueryParameters(subjectAttributes)

        and: 'the header parameters are created for the request'
        def actualHeaderParameters = jsonAttributeDAP.createHeaderParameters(subjectAttributes)

        then: 'the query parameters are empty'
        actualQueryParameters.isEmpty()

        and: 'the header parameters contain the expected entries'
        actualHeaderParameters == headerMap

        where:
        configParameterMappings                               | subjectAttributesMap            | headerMap
        [[name: 'username', useValueOfAttribute: 'a']]        | [a: 'hello', subject: 'doe']    | [sub: 'ZG9l', 'username': 'aGVsbG8=']
        [[name: 'subject', staticValue: 'bruno']]             | [a: 'hello', subject: 'doe']    | [sub: 'ZG9l', 'subject': 'YnJ1bm8=']
        [[name: 'subject', useValueOfAttribute: 'not_there']] | [a: 'hello', subject: 'doe']    | [sub: 'ZG9l']
        [[name: 'name', staticValue: 'bruno']]                | [a: 'hello', subject: 'doe']    | [sub: 'ZG9l', 'name': 'YnJ1bm8=']
        [[name: 'username', useValueOfAttribute: 'a']]        | [a: 'björn öl', subject: 'doe'] | [sub: 'ZG9l', 'username': 'YmrDtnJuIMO2bA==']
        [[name: 'subject', staticValue: 'brüno']]             | [a: 'hello', subject: 'doe']    | [sub: 'ZG9l', 'subject': 'YnLDvG5v']
        [[name: 'subject', staticValue: 'brüno'],
         [name: 'username', useValueOfAttribute: 'a']]        | [a: 'hello', subject: 'doe']    | [sub     : 'ZG9l',
                                                                                                   subject : 'YnLDvG5v',
                                                                                                   username: 'aGVsbG8=']
    }

    @Unroll
    "The attributes are retrieved correctly from a JSON response provided by the JSON backend"() {
        given: 'a JSON Response body as expected from a JSON backend'
        final now = Instant.now().epochSecond
        String validJsonResponseBody =
                """|
                |{
                |  "subject": "the-subject",
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

        and: 'A JSON DAP using mocked configuration'
        def jsonAttributeDAP = new JsonAttributeDataAccessProvider(Mock(JsonDataAccessProviderConfiguration) {
            json() >> Stub(Json) {
                fromJson(validJsonResponseBody) >> new JsonSlurper().parseText(validJsonResponseBody)
            }
        })

        when:
        def actualAttributes = jsonAttributeDAP.getAttributesFrom(httpResponse)

        then:
        actualAttributes != null
        actualAttributes.size() == 5
        actualAttributes['subject']?.value == "the-subject"
        actualAttributes['subject'].name.format == 'json'
        actualAttributes['now']?.value == now
        actualAttributes['static-list']?.value == ["squirtle", "wartortle", "blastoise"]
        actualAttributes['static-value']?.value == 'final.'
        actualAttributes['https://ws-fed.style.com/claim/name'].value == 'ash'
        actualAttributes['https://ws-fed.style.com/claim/name'].name.format == 'json'
    }

}
