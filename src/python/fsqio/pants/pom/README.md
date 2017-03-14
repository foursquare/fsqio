# Installation

Copy these files from the fsqio repo:
```
src/python/fsqio/__init__.py
src/python/fsqio/pants/__init__.py
src/python/fsqio/pants/pom/*
```

You can delete `src/python/fsqio/pants/pom/BUILD` unless you're planning on writing tests for or publishing the pom-resolve; this means you don't need to put the deps in your 3rdparty file.

Add this to your pants.ini:

```
# Note this is effectively requirements.txt for the pants environment, so we need the deps of
# our build dependancies, see: https://github.com/pantsbuild/pants/issues/4001
[GLOBAL]
plugins: +[
    "requests==2.5.3",
    "requests-futures==0.9.4",
   ]
backend_packages: +[
    "fsqio.pants.pom",
  ]
[pom-resolve]
# Fill in these values as appropriate; there's good defaults in fsqio's pants.ini
maven_repos: [
    ...
  ]
global_exclusions = [
    ...
  ]
global_pinned_versions = [
    ...
  ]
local_override_versions = [
    ...
  ]

[cache.pom-resolve]
write_to: ...
read_from: ...
```
