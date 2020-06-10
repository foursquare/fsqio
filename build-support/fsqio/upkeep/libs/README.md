## Upkeep functions
Fetcher.sh downloads hosted artifacts and uses them to bootstrap the development environment used by Pants.

Fetcher bootstraps and installs our Python distribution internally. Written in bash, we can bootstrap development environments across our fleet without inheriting breaks from upstream Python and the sort.

### Fetcher.sh
* Bootstrap static resources from hosting.
* Download resources exactly once per version
* Long-lived centralized cache
    - safe for arbitrary concurrent readers/writers
        * Confirmed safe to share across with production of up to 20 concurrent CI clients

#### Cache
Fetcher cache can be safely shared by concurrent clients, with fully atomic read/writes.

We moved to containerized CI and single-use development environments across all workloads. Fetcher cache was written to optimize bandwidth costs of bootstrapping fresh development environment every job.

With a primed cache, updates only trigger for new/upgraded libraries. Tear down and spin up of the chrooted development environment generally around 15s for linux and 30s on OSX (mostly in Homebrew).
