# If we internally fork an otherwise publicly available jar, add the public jar targets here.

liftVer = '2.6.2'
jar_library(
  name = 'lift-mongodb',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
      scala_jar(org = 'net.liftweb', name = 'lift-mongodb', rev = liftVer, intransitive = True),
  ],
  dependencies = [
    '3rdparty:mongo-java-driver-unpacked',
  ],
)

jar_library(
  name = 'lift-common',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    scala_jar(org = 'net.liftweb', name = 'lift-common', rev = liftVer)
  ]
)

jar_library(
  name = 'lift-json',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    scala_jar(org = 'net.liftweb', name = 'lift-json', rev = liftVer)
  ]
)

jar_library(
  name = 'lift-record',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    scala_jar(org = 'net.liftweb', name = 'lift-record', rev = liftVer)
  ]
)

jar_library(
  name = 'lift-mongodb-record',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    scala_jar(org = 'net.liftweb', name = 'lift-mongodb-record', rev = liftVer, intransitive = True),
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
    scala_jar(org = 'net.liftweb', name = 'lift-util', rev = liftVer)
  ]
)

jar_library(
  name = 's2',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    jar(org = 'org.jnegre.google', name = 's2-geometry', rev = '1.0')
  ]
)
