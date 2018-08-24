# JVM Properties file

The logging.properties is the default config for the JUL logger (aka `java.util.logging`).

## Motivation
The JVM loads logging framework based off classpath contents, which means that if a target doesn't explicitly depend on a specific logging framework, the JVM falls back the JUL.

For instance, when run internally, [Rogue](../../../../jvm/io/fsq/rogue) uses Foursquare's `slf4j` wrapper - but when run in the strict context of Fsq.io, it falls back to JUL logging.

#### Example
The primary use case here is for Fsq.io, since internally we configure these modules to use slf4j. The properties file here quiets the **very** chatty `mongodb` logging

* **Without** logging config:

        <much snipping>
        Aug 24, 2018 9:35:09 AM com.mongodb.diagnostics.logging.JULLogger log
        INFO: Opened connection [connectionId{localValue:59, serverValue:199}] to localhost:27017
        Aug 24, 2018 9:35:09 AM com.mongodb.diagnostics.logging.JULLogger log
        INFO: Opened connection [connectionId{localValue:63, serverValue:200}] to localhost:27017
        ......
        Time: 0

* **With** logging config:

        OK (0 tests)

        ........................................................................................................................................................................
        Time: 0


## Config
Passing logging config to the JUL backend is done through a JVM option:

        -Djava.util.logging.config.file=${BUILD_ROOT}/src/resources/props/logging.properties

We export this to Pants as an environmental variable, see `PANTS_JVM_TEST_JUNIT_OPTIONS` in the env.sh file.
