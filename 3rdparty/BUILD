# If we internally fork an otherwise publicly available jar, add the public jar targets here.

def sjar(org, name, **kwargs):
  scala_jar_version = '2.11'
  return jar(org = org, name = '%s_%s' % (name, scala_jar_version), **kwargs)

# Keep in sync with 3rdparty/BUILD.hadoop
HADOOP_VERSION = '2.6.0'

jar_library(
  name = 'hadoop-common',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    jar(org = 'org.apache.hadoop', name = 'hadoop-common', rev = HADOOP_VERSION),
    # TODO: Figure out how dependency_management keys really work so that we don't
    # clobber artifact types with "managed" values.
    # See src/python/foursquare/pants/pom/pom.py
    jar(org = 'org.apache.hadoop', name = 'hadoop-auth', rev = HADOOP_VERSION),
  ],
  dependencies = [
    ':slf4j-all',
  ],
)

jar_library(
  name = 'hadoop-mapreduce-client',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    jar(org = 'org.apache.hadoop', name = 'hadoop-mapreduce-client-core', rev = HADOOP_VERSION),
  ],
  dependencies = [
    ':hadoop-common',
  ],
)

# TODO: This is only here while we transition twofishes to our hfile reader.
jar_library(
  name = 'hbase',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    jar(org = 'org.apache.hbase', name = 'hbase', rev = '0.94.6')
  ]
)

liftVer = '2.6.2'
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

jar_library(
  name = 'specs',
  tags = [
    'opensource',
    'fscommon',
  ],
  jars = [
    sjar(org = 'org.scala-tools.testing', name = 'specs_2.10', rev = '1.6.9'),
  ],
  dependencies = [
    ':mockito'
  ],
)
