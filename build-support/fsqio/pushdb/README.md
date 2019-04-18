## Publish Pushdb

The pushdb is Pants database of publish information. Each published jar will have an entry with the library's version and commit it was created from.


### Fsq.io
Fsq.io is generally a strict subset of our internal repo - changes only originate internally and are migrated out to Fsq.io.

The pushdb is the only exception - these files originate in Fsq.io and are backported internally. Jars are published directly from a checkout of Fsq.io, _not_ our internal repo.

      ./pants publish \
      --no-gen-spindle-write-annotations-json \
      --doc-scaladoc-include-codegen \
      src/jvm/io::

This is so external consumers of an Fsq.io library can cross-reference the jars and have commits that represent the actual publish.
