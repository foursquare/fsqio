# If we internally fork an otherwise publicly available jar, add the public jar targets here.

def sjar(org, name, **kwargs):
  scala_jar_version = '2.10'
  return jar(org = org, name = '%s_%s' % (name, scala_jar_version), **kwargs)

liftVer = '2.6.2-fs-a'
jar_library(
  name = 'lift-mongodb',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
      sjar(org = 'net.liftweb', name = 'lift-mongodb', rev = liftVer, intransitive = True),
  ],
  dependencies = [
    '3rdparty:mongodb',
  ],
)

jar_library(
  name = 'lift-common',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    sjar(org = 'net.liftweb', name = 'lift-common', rev = liftVer)
  ]
)

jar_library(
  name = 'lift-json',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    sjar(org = 'net.liftweb', name = 'lift-json', rev = liftVer)
  ]
)

jar_library(
  name = 'lift-record',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    sjar(org = 'net.liftweb', name = 'lift-record', rev = liftVer)
  ]
)

jar_library(
  name = 'lift-mongodb-record',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    sjar(org = 'net.liftweb', name = 'lift-mongodb-record', rev = liftVer, intransitive = True),
  ],
  dependencies = [
    ':lift-mongodb',
    ':lift-record',
  ],
)

# NOTE: this is just a convenient wrapper to depend on for anything in net.liftweb.mongodb._
target(
  name = 'lift-mongo',
  tags = [
    'opensource',
    'fscommon',
  ],
  dependencies = [
    ':lift-mongodb',
    ':lift-mongodb-record',
  ],
)

jar_library(
  name = 'lift-util',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    sjar(org = 'net.liftweb', name = 'lift-util', rev = liftVer)
  ]
)

jar_library(
  name = 'mongodb',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    jar(org = 'org.mongodb', name = 'mongo-java-driver', rev = '2.13.0-rc0')
  ]
)
