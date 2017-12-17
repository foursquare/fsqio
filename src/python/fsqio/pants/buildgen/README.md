## Buildgen

Buildgen lexically parses import statements and updates existing BUILD files, ensuring correctness and removing human error.

### Installation
The buildgen modules are [published to Pypi](https://pypi.python.org/pypi/fsqio.pants.contrib.buildgen.core) as [Pants plugins](https://www.pantsbuild.org/howto_plugin.html).

To install from the published plugins, you can try using the upstream Pants `plugins` to automatically consume from pypi.

      plugins: [
          "fsqio.pants.contrib.buildgen.core==1.3.0",
          "fsqio.pants.contrib.buildgen.jvm==1.3.0",
          "fsqio.pants.contrib.buildgen.python==1.3.0",
        ]

Some people have had trouble getting their Pants virtualenv to play nicely with the buildgen modules.
In that case, an escape hatch can be to install into the Pants virtualenv by hand.

Assuming you use the standard location for your bootstrapped Pants install:

      ~/.cache/pants/setup/bootstrap/${pants_version}/bin/pip install fsqio.pants.buildgen.core fsqio.pants.buildgen.jvm fsqio.pants.buildgen.python

That should be it!


#### Troubleshooting
If you have multiple virtualenvs (or if Pants just can't find the buildgen backend),
you can add the installation path to your pythonpath in pants.ini


      [GLOBAL]

       pythonpath: [
           "%(homedir)s/.cache/pants/setup/bootstrap/1.3.1rc1/lib/python2.7/site-packages"
          ]
