package io.curity.identityserver.plugin.data.access.rest;

import io.curity.identityserver.plugin.data.access.rest.config.RestDataAccessProviderConfiguration;
import se.curity.identityserver.sdk.datasource.CredentialDataAccessProviderFactory;
import se.curity.identityserver.sdk.datasource.CredentialManagementDataAccessProvider;

public final class RestCredentialDataAccessProviderFactory implements CredentialDataAccessProviderFactory
{
    private final RestDataAccessProviderConfiguration _configuration;

    public RestCredentialDataAccessProviderFactory(RestDataAccessProviderConfiguration configuration)
    {
        _configuration = configuration;
    }

    @Override
    public CredentialManagementDataAccessProvider create()
    {
        return new RestCredentialDataAccessProvider(_configuration);
    }
}
