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

package io.curity.identityserver.plugin.descriptor;

import io.curity.identityserver.plugin.data.access.json.JsonAttributeDataAccessProvider;
import io.curity.identityserver.plugin.data.access.json.JsonCredentialDataAccessProvider;
import io.curity.identityserver.plugin.data.access.json.config.JsonDataAccessProviderConfiguration;
import se.curity.identityserver.sdk.Nullable;
import se.curity.identityserver.sdk.config.Configuration;
import se.curity.identityserver.sdk.plugin.descriptor.DataAccessProviderPluginDescriptor;
import se.curity.identityserver.sdk.datasource.AttributeDataAccessProvider;
import se.curity.identityserver.sdk.datasource.CredentialDataAccessProvider;

@SuppressWarnings("unused")
public class JsonDataAccessPluginDescriptor implements DataAccessProviderPluginDescriptor
{
    @Override
    public String getPluginImplementationType()
    {
        return "json";
    }

    @Override
    public Class<? extends Configuration> getConfigurationType()
    {
        return JsonDataAccessProviderConfiguration.class;
    }

    @Nullable
    @Override
    public Class<? extends CredentialDataAccessProvider> getCredentialDataAccessProvider()
    {
        return JsonCredentialDataAccessProvider.class;
    }

    @Nullable
    @Override
    public Class<? extends AttributeDataAccessProvider> getAttributeDataAccessProvider()
    {
        return JsonAttributeDataAccessProvider.class;
    }

}