## Spindle codegen package

The Spindle codegen binary(`src/jvm/io/fsq/spindle/codegen:binary`) has a circular dependency on
generated Spindle. If you want to make a change to Spindle codegen files, you must
have Spindle-generated thrift descriptors in order to compile that change.

This poses a problem.

We were forced to make a custom Pants plugin to handle this reality. This included checking-in
some known-good Spindle codegen, to be used solely during compiles of the Spindle codegen binary.
Upon changes to the codegen binary source, the plugin shells out to Pants mid-execution and
compiles only the codegenerator targets (and dependencies) in a Spindle-isolated sub run.

The workflow looks something like:

Run Pants
  do stuff
    if spindle transitive deps change:
       compile Spindle
  Spindle codegen rest of world
  compile rest of world
exit

You can see that process in the src/fsqio/pants/spindle files.

### Tags
All dependencies of src/jvm/io/fsq/spindle/codegen:binary must have the spindle_codegen tag,
and cannot have the spindle_runtime tag. This requirement hopes to keep the shelled run
from growing to include any more targets.
