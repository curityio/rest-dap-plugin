# JSON Data Access Plugin Example

[![Quality](https://img.shields.io/badge/quality-demo-red)](https://curity.io/resources/code-examples/status/)
[![Availability](https://img.shields.io/badge/availability-source-blue)](https://curity.io/resources/code-examples/status/)

This repository shows an example Data Access Provider Plugin for the Curity Identity Server. It shows how to use a RESTful JSON web service as a data source.

Note that the Curity Identity Server has a JSON Data Access Provider bundled if you need the functionality in a production environment. This repository serves as a demo of how such a feature can be implemented. You can use it to base your work on.

## Building the Plugin

Build the plugin by running `mvn package`. This will produce a JAR file in the `target` directory, which can be installed.

## Installing the Plugin

To install the plugin, copy the compiled JAR into `${IDSVR_HOME}/usr/share/plugins/${pluginGroup}` on each node, including the admin node. For more information about installing plugins, refer to the [plugins documentation](https://curity.io/docs/identity-server/developer-guide/plugins/index.html#plugin-installation).

## Running Tests

Run unit tests with:

```bash
mvn test
```
Or to run a specific test class use:

```bash
mvn test -Dtest=JsonCredentialDataAccessProviderSpecification
```

## More Information

Please visit [curity.io](https://curity.io/) for more information about the Curity Identity Server.
