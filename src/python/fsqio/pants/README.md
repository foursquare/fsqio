# Pants plugins

Foursquare's opensource Pants plugins are under this root.

## Available as plugins from PyPi
[We publish some of these plugins on PyPi](https://pypi.python.org/pypi?%3Aaction=search&term=fsqio&submit=search), including buildgen and pom-resolve.

Publishing as plugins means enables arbitrary Pants projects can consume just by adding config. There is some light documentation on how to do that at the buildgen [[README.md|pants('src/python/fsqio/pants/buildgen:page')]].

### How to publish to PyPi

The `Fsq.io` packages are registered to PyPi under the `opensource@foursquare.com` address email. Publishing an updated module requires the Fsq.io GPG key, you should ask the infrastructure team who has access to that.

### 1. Package the modules as sdists

1. Bump the version!
    - Set in the BUILD file for the target `python_library`
    - e.g. [src/python/fsqio/pants/buildgen/core/BUILD](https://github.com/foursquare/foursquare.web/blob/master/src/python/fsqio/pants/buildgen/core/BUILD)
     !inc[start-at=provides=setup_py(](./buildgen/core/BUILD)
1. Build the modules
    - `./pants setup-py src/python/fsqio::`
    - Look for the output tar.gz files in `dist/`
1. Optionally verify that the new tarballs work.

### 2. Publish to PyPI
Now that you have built the packages, we can upload them to PyPi. I use `twine` to handle the sign/upload.

1. Use your registered `foursquare.com` GPG key and password
    - This key must be registered to an owner of the `fsqio` account on PyPi.
    - Make sure that you see the key when you run `gpg --list-keys`.
1. Install `twine`.
      - `pip install twine`
1. Configure your pypi credentials
      - Create a `~/.pypirc` file:

            [pypi]
            username: <name>
            password: <password>

            [server-login]
            username: <name>
            password:<password>

1. Sign each `fsqio` buildgen package under `dist` in the pants checkout.
      - I use the `-u` flag to make sure I am using the correct GPG key

            cd dist
            gpg --detach-sign -u $PUB-KEY_DIGEST -a fsqio.pants.<package>-1.?.?.tar.gz

      - The `dist` folder should now have an `.asc` file for each of the buildgen modules.
1. Upload to PyPi

      - The `.asc` must be uploaded along with the package so that the signature can be verified.

             twine upload fsqio.pants.<package>-1.?.?.tar.gz fsqio.pants.<package>-1.?.?.tar.gz.asc

Now go to PyPi and make sure that the packages are there. You are done!
