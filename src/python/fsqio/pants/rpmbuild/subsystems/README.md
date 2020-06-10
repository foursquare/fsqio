# Pants Remote Sources subsystem
Binary management subsystem for Pants.

## Usage
### Host
Artifact must be available from URL configured in options, e.g.
```
binaries_baseurls: [
    "s3://binaries.pantsbuild.org
  ]
```

### BUILD file registration
```
remote_source(
  name='node.tar.gz',
  version="v8.9.0",
)

rpm_spec(
  spec='nodejs.spec',
  defines={
    'version': node_ver,
    'release': '0',
  },
  dependencies = [
    ':node.tar.gz',
  ]
)
```
That node.tar.gz will be downloaded and cached in the bootstrapdir. Artifacts can be unpacked and bundled into build artifacts.

## BinaryUtils
Pants BinaryUtils framework is a subsystem leveraged by upstream tasks to fetch, cache, and invoke prebuild binary tooling.

Backed by a powerful namespacing schema, Python tasks can concisely register tooling and get bootstrap and configuration for free.

### Problem
BinaryUtils created an atomic caching system for prebuilt packages but declined to surface a public API. Binary registration only supported within the Python tasks with high overhead and learning curve.

We had just written our first RpmBuild plugin, and RpmTargets needed a first-class mechanism for bootstrapping source code bundles

### Goals
1. Fetch version artifacts from hosting
1. Fingerprint only by version number
1. Register additional binary artifacts in BUILD files instead of tasks
1. Centralized cache safe for concurrent clients
    * Download artifacts exactly once per version
    * Time/bandwidth untenable for large sizes
        - libpostal data package is over 1gb, for instance

### Prior Art
1. `DeferredSources`, an early upstream Pants task
    * Potentially induced to meet goals
    * Did not pursue because:
        - Specialized for jars
        - Unpacks every artifact each run
        - Downloads artifacts every build

## RemoteSource framework
Fetcher for remote sources which uses BinaryToolBase pipeline. Introduces BUILD file registration and long-lived caching for hosted artifacts.

### Features
RemoteSources plugin provides the following features (uniquely or otherwise):
 * Long-lived caching of downloaded files
    - Only invalidated by version changes - otherwise considered cached
    - Kept outsidce .pants.d or artifact cache, alongside Pants downloaded tooling.
    - Atomic downloads so we aren't poisoned by corrupted downloads.
 * Addressable in BUILD files
    - These are considered "versioned" and can be referenced as dependencies.
    - RpmBuilder as canonical consumer - caching bootstrapped source bundles.
 * Fetched on demand, either directly or transitively
    - If you call `./pants rpmbuild src/redhat/libevent` only then should it bootstrap the source bundle.
 * Unpack attribute in the target
    - Extract as an addressable feature.

### Future and/or Deprecation
We discussed this subsystem with upstream Pants, which resulted in Pants adding a subset of these features. That subset needs to be audited and consumed, with a longer-term goal of patching upstream to add any of the following features we cannot live without.

#### TODOs
1. Either fully adapt the remote_sources plugin for the new BinaryToolBase interface or work with upstream until UnpackJars is robust enough for our use cases.
1. Adopt platform-independent syntax wherever possible
    * Extremely tedious to copypaste artifacts to every possible OSX version, especially for packages that don't care about the distinction.
1. Decouple RemoteSources framework from RpmBuilder client.
