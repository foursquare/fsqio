## Tag validation

Tags are used to enforce rules upon the build graph.
The common case is to restrict what can depend upon a given target, or to restrict the dependees of a target.

### Supported Tag types

      dependees_must_have
      dependencies_must_have
      dependencies_cannot_have

Tag validation is forced to happen as a prerequisite of codegen (`./pants gen` or a dependent task like compile/test).

Note: We exempt 3rdparty from tag requirements. Exemptions are done at directory level, not target level.
### Usage

An example using `dependencies_must_have`:

      scala_library(
        name = 'util',
        sources = globs('*.scala'),
        tags = ['fscommon', 'dependencies_must_have:fscommon'],
        dependencies = [
          '3rdparty:mongodb',
        ],
      )

Adding dependencies without that tag causes build failures.

      17:00:20 00:00     [parse]
                     Executing tasks in goals: tag -> validate
                     17:00:20 00:00   [tag]
      17:00:20 00:00     [tag]
      17:00:20 00:00   [validate]
      17:00:20 00:00     [validate]
                         Invalidated 4 targets.
                         src/jvm/io/fsq/foo:foo requires dependencies to have tag fscommon and thus cannot depend on src/jvm/io/fsq/bar:bar

### Using in a separate Pants repo
This should be a published Pants plugin on Pypi. But we haven't gotten to that. In the interim:

1. Copy these files from the foursquare/fsqio repo to your repo:

       src/python/fsqio/__init__.py
       src/python/fsqio/pants/__init__.py
       src/python/fsqio/pants/tags/__init__.py
       src/python/fsqio/pants/tags/register.py
       src/python/fsqio/pants/tags/validate.py

2. Add config similar to below.

       [GLOBAL]
       pythonpath: +[
           "%(buildroot)s/src/python",
         ]
         backend_packages: +[
           "fsqio.pants.tags",
         ]

         [tag]
       by_prefix:  {
           "3rdparty": ["exempt"],
           "tests": ["tests"],
           "src": ["dependencies_cannot_have:tests"]
         }
