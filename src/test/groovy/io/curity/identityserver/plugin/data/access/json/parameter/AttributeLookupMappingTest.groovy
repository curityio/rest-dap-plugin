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

package io.curity.identityserver.plugin.data.access.json.parameter

import se.curity.identityserver.sdk.attribute.Attribute
import se.curity.identityserver.sdk.attribute.AttributeName
import se.curity.identityserver.sdk.attribute.Attributes
import se.curity.identityserver.sdk.attribute.NullAttributeValue
import se.curity.identityserver.sdk.attribute.SubjectAttributes
import spock.lang.Specification
import spock.lang.Unroll

class AttributeLookupMappingTest extends Specification {

    @Unroll
    def "Lookup #name value based on name"() {
        given: 'SubjectAttributes with some values'
        SubjectAttributes attributes = SubjectAttributes.of('foo', Attributes.of(name, value))

        and: 'An attribute lookup mapping'
        AttributeLookupMapping mapping = new AttributeLookupMapping('hello', name)

        when: 'Getting a value'
        String mappedValue = mapping.getMappedValue(attributes)

        then: 'The mapping is found, and is a string'
        mappedValue == result

        where:
        name      | value | result
        'number'  | 5     | '5'
        'boolean' | true  | 'true'
        'string'  | 'foo' | 'foo'
    }

    @Unroll
    def "Lookup null value based on name"() {
        given: 'SubjectAttributes with some values'
        List list = [Attribute.of(AttributeName.of('foo'), NullAttributeValue.instance)]
        SubjectAttributes attributes = SubjectAttributes.of('bar', Attributes.of(list))

        and: 'An attribute lookup mapping'
        AttributeLookupMapping mapping = new AttributeLookupMapping('hello', 'foo')

        when: 'Getting the value'
        String mappedValue = mapping.getMappedValue(attributes)

        then: 'The mapping is null'
        mappedValue == null
    }
}
