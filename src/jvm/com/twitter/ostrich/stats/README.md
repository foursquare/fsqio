Ostrich
=======

## NOTE ##

**Please think very hard about modifying this package or consuming it outside of existing
usages, it is almost certainly not what you want to be using!**

## About ##

This package is a partial fork of Twitter's now archived stats library, 
[Ostrich](https://github.com/twitter/ostrich/tree/ostrich-9.27.0). The code here is more
or less pulled in as is, the summary of key changes being:

 - [BackgroundProcess.scala](https://github.com/twitter/ostrich/blob/ostrich-9.27.0/src/main/scala/com/twitter/ostrich/admin/BackgroundProcess.scala)
   is pulled in under this package instead of a separate admin package.
 - some existing code was deleted and some files left out as unused
 - this package is subject the same `scalafmt` linting rules as the rest of our codebase
 - the `BUILD` file here is our own, and marked as such
 - the core reason this was pulled in: to update its use of twitter-util implicit
   conversions removed in later versions

Corresponding tests are pulled in under `test/jvm/com/twitter/ostrich/stats` in our
internal codebase, but not redistributed as part of fsqio.
