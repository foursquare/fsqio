# Fsq.io Intellij helpers

## Lists.scala for Intellij

### Background

scala/Lists.scala uses Type Constructors which IntelliJ can't parse ([issue](https://youtrack.jetbrains.com/issue/SCL-7974)).

We created `scripts/fsqio/IntelliJ/Lists.scala` which features simpler interfaces specifically for the IntelliJ presentation compiler.

### Usage
Compile Lists.Implicits._
Right-click `src/jvm/io/fsq/common/scala/Lists.scala` and select Mark as Plain Text


## Spindle stubs for Intellij

### Background

Our generated code is enormous and may be the source of slow indexing and memory issues, especially when running multiple projects. By stubbing out our codegen we can significantly reduce the size and complexity of the classes that IntelliJ has to index and this seems to improve performance.

The code generation templates for the stub classes can be tweaked in the template files under `src/resources/io/fsq/ssp/codegen/scalainterface`

Note that currently only the spindle classes are included. We could extend this to cover soy template and other generated classes as well if it is important for you to have those analyzed by the IDE.


### Usage

Generate an interface-only stub version of the codegen classes:

      ./pants ide-gen src/thrift::

* Be sure to include `scripts/fsqio/IntelliJ` as a source directory in your project.
* You do not need to include anything under `.pants.d`.
* You can exclude the `scripts/fsqio/IntelliJ/spindle_stubs/scalate_workdir` subdirectory created during codegen.

For now, you will need to re-run the ide-gen command any time you update the stub classes.
