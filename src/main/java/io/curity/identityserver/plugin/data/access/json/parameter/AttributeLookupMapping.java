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

package io.curity.identityserver.plugin.data.access.json.parameter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.curity.identityserver.sdk.Nullable;
import se.curity.identityserver.sdk.attribute.Attribute;
import se.curity.identityserver.sdk.attribute.PrimitiveAttributeValue;
import se.curity.identityserver.sdk.attribute.SubjectAttributes;

public class AttributeLookupMapping extends ParameterMapping
{
    private static final Logger _logger = LoggerFactory.getLogger(AttributeLookupMapping.class);

    private final String _attributeLookupName;

    public AttributeLookupMapping(String name, String attributeLookupName)
    {
        super(name);
        _attributeLookupName = attributeLookupName;
    }

    @Nullable
    @Override
    public String getMappedValue(SubjectAttributes attributes)
    {
        @Nullable String value = null;
        @Nullable Attribute attribute = attributes.get(_attributeLookupName);
        if (attribute != null && attribute.getAttributeValue() instanceof PrimitiveAttributeValue)
        {
            value = String.valueOf(attribute.getValue());
            _logger.debug("Mapping parameter {} to {}.", getParameterName(), value);
        }
        else if (attribute == null)
        {
            _logger.debug("Could not map attribute {}. Attribute not found.", _attributeLookupName);
        }
        else
        {
            _logger.debug("Could not map attribute {}. Attribute value is not primitive.", _attributeLookupName);
        }
        return value;
    }
}
