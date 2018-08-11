# SOMns Extension for Testing

This is a SOMns extension module, which is used to test the SOMns functionality.

Similar to SOMns, the extension uses `ant` as build systems.

The `build.xml` file defines the various build tasks. It contains examples of
how to create the `somns.properties` file (see the `compile` task) as well as
an example for adding a JAR dependency to the module's own `Class-Path`
property in its manifest file (see the `jar` task).
