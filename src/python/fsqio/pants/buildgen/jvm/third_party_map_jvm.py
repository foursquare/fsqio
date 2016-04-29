# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from fsqio.pants.buildgen.core.third_party_map_util import Default, Skip


# This non-exhaustive map covers many common package names imported via 3rdparty jars.
# TODO(ryan): do some kind of real lookup here, e.g. iterate over JarDependencies, list files, look for appropriate
# packages.
jvm_third_party_map = {
  'akka': 'akka',
  'backtype': {
    'storm': 'storm-core',
  },
  'breeze': 'breeze',
  'ch': {
    'qos': {
      'logback': 'logback',
    },
  },
  'cascading': 'cascading',
  'cc': {
    'factorie': 'factorie',
  },
  'com': {
    'amazon': {
       'speech': 'alexa-skills-kit',
       'emr': 'amazon-emr-kinesis'
    },
    'amazonaws': 'amazonaws',
    'beeswax': 'beeswaxrtb',
    'clearspring': {
      'analytics': {
        'stream': 'stream-lib',
       }
    },
    'codahale': {
      'jerkson': 'jerkson',
    },
    'carrotsearch': 'langid-java',
    'cybozu': {
      'labs': 'language-detect',
    },
    'drew': 'metadata-extractor',
    'esotericsoftware': {
      'kryo': 'kryo',
    },
    'esri': {
      'core': 'esri-geometry-api',
      'hadoop': 'spatial-sdk-hive'
    },
    'fasterxml': {
      'jackson': 'jackson-scala',
    },
    'foursquare': {
      'common': {
        'async': 'twitter-util-async',
      },
      'datafiles': {
        Default: 'geotouches'
      },
      'esh230': 'elasticsearch-hadoop-mr-230',
      'geo': {
        'quadtree': 'country-revgeo',
        'shapefile': {
          'continent': 'continent-shapefiles',
          'country': 'cc-shapefiles',
          'dma': {
            'DmaByNameShapefile': 'dma-name-shapefiles',
            'DmaByIDShapefile': 'dma-id-shapefiles',
          },
          'metro': 'metro-shapefiles',
          'state': 'state-shapefiles',
          'timezone': 'tz-shapefiles',
          'zcta': 'zcta-shapefiles',
        },
      },
      'jedis281': {
          'redis': {
              'clients': 'jedis281',
          },
      },
      'kafka9': 'kafka9-clients'
    },
    'github': {
      'fakemongo': 'fongo',
      'mustachejava': 'mustache-java'
    },
    'google': {
      'api': {
        'client': {
          'googleapis': {
            'auth': {
              'oauth2': 'libmirror'
            },
            'javanet': 'gdata'
          },
          'auth': {
            'oauth2': 'gdata'  # note(stefano): this probably shouldn't be in gdata
          },
          'http': 'libmirror',
          'json': 'libmirror',
          'util': 'gdata'
        },
        'services': {
          'drive': 'gdata',
          'mirror': 'libmirror'
        }
      },
      'caliper': 'caliper',
      'closure': {
        'compiler': 'closure-compiler',
        'templates': 'closure-templates',
      },
      'common': {
        'annotations': 'guava',
        'base': 'guava',
        'cache': 'guava',
        'collect': 'guava',
        'geometry': 's2',
        'hash': 'guava',
        'io': 'guava',
        'primitives': 'guava',
        'util': 'guava',
      },
      'gdata': 'gdata',
      'i18n': {
        'phonenumbers': {
          'geocoding': 'phonenumbers-geocoder',
          Default: 'phonenumbers'
        }
      },
      'inject': 'guice',
      'javascript': 'closure',
      'template': 'closure-templates',
      'zxing': 'zxing',
    },
    'googlecode': {
        'concurrentlinkedhashmap': 'concurrentlinkedhashmap'
    },
    'ibm': {
      'icu': 'icu4j',
    },
    'infochimps': {
      'elasticsearch': 'wonderdog'
    },
    'jcraft': 'jsch',
    'maxmind': 'geoip',
    'mongodb': {
      Default: 'mongodb',
      'casbah': 'casbah',
    },
    'mchange': {
      'v2': {
        'c3p0': 'c3p0'
      }
    },
    'mysql': 'mysql',
    'novus': 'salat',
    'opencsv': 'opencsv',
    'rockymadden': 'rockymadden',
    'thoughtworks': Skip,
    'sun': {
      'jna': 'jna',
      'net': {
        'httpserver': Skip,
      },
    },
    'thimbleware': {
      'jmemcached': 'jmemcached-daemon',
    },
    'twitter': {
      'algebird': 'algebird',
      'common': 'zookeeper-lock',
      'concurrent': 'twitter-util',
      'conversions': 'twitter-util',
      'elephantbird': 'elephant-bird',
      'finagle': {
        'memcached': {
          'NoReplicationClient': None,
          Default: 'finagle',
        },
        Default: 'finagle',
      },
      'hashing': 'twitter-util',
      'json': 'twitter-json',
      'logging': 'twitter-util',
      'ostrich': 'ostrich',
      'parrot': 'iago',
      'penguin': 'korean-text-scala-2.10',
      'scalding': 'scalding',
      'thrift': {
        'ServiceInstance': 'zookeeper-lock',
      },
      'util': 'twitter-util',
      'zookeeper': 'zookeeper-client',
    },
    'typesafe': {
      'config': 'typesafe-config',
    },
    'vividsolutions': {
      'jts': 'jts',
    },
    'wcohen': {
      'ss': 'secondstring',
    },
  },
  'datafu': 'datafu',
  'de': {
    'bwaldvogel': {
      'liblinear': 'liblinear',
    },
    'micromata': {
      'opengis': 'JavaAPIforKml'
    }
  },
  'difflib': 'java-diff-utils',
  'edu': {
    'upc': 'freeling',
  },
  'io': {
    'netty': 'netty4',
  },
  'java': Skip,
  'javax': {
    'mail': 'mail',
    Default: Skip,  # NOTE(ryan): I can't find where this lives
  },
  'jskills': 'jskills',
  'kafka': {
    'api': 'kafka',
    'common': 'kafka',
    'consumer': 'kafka',
    'etl': 'kafka-hadoop-consumer',
    'message': 'kafka',
    'producer': 'kafka',
    'serializer': 'kafka',
    'utils': 'kafka',
  },
  'kr': {
    'ac': {
      'kaist': {
        'swrc': 'jhannanum_cprw',
      }
    }
  },
  'kylm': 'kylm',
  'net': {
    'jpountz': {
      'lz4': 'lz4',
    },
    'liftweb': {
      'actor': 'lift-actor',
      'builtin': 'lift-webkit',
      'common': 'lift-common',
      'db': 'lift-db',
      'http': 'lift-webkit',
      'json': 'lift-json',
      'mapper': 'lift-mapper',
      'mocks': 'liftweb-testkit',
      'mongodb': 'lift-mongo',
      'proto': 'lift-proto',
      'record': 'lift-record',
      'sitemap': 'lift-webkit',
      'util': 'lift-util',
    },
    'fortuna': {
      'ical4j': 'ical4j',
    },
    'sf': {
      'uadetector': 'uadetector'
    }
  },
  'nl': {
    'captcha': 'simplecaptcha',
  },
  'opennlp': 'opennlp',
  'org': {
    'apache': {
      'axis2': 'axis2',
      'commons': {
        'cli': 'commons-cli',
        'codec': 'twitter-util',
        'compress': 'commons-compress',
        'fileupload': 'commons-fileupload',
        'httpclient': 'commons-httpclient',
        'io': 'commons-io',
        'lang': 'commons-lang',
        'math': 'commons-math',
        'math3': 'commons-math3',
        'net': 'commons-net',
        'validator': 'commons-validator',
      },
      'curator': {
        Default: 'curator',
        'test': 'curator-test',
      },
      'hadoop': {
        # NOTE(ryan): org.apache.hadoop is a complicated soup of dependencies
        'conf': 'hadoop-common',
        'filecache': 'hadoop-mapreduce-client',
        'fs': 'hadoop-common',
        'hbase': {
          'io': {
            'hfile': {
              Default: 'hbase',
              'hacks': None,
            },
          },
          'util': 'hbase',
        },
        'hdfs': 'hadoop-hdfs',
        'hive': {
          Default: 'hive-exec',  # TODO(joe): this isn't the whole story
          'common': 'hive-common'
        },
        'http': 'hadoop-common',
        'io': 'hadoop-common',
        'mapred': 'hadoop-mapreduce-client',
        'mapreduce': {
          Default: 'hadoop-mapreduce-client',
          'lib': {
            Default: 'hadoop-mapreduce-client',
            'output': {
              Default: 'hadoop-mapreduce-client',
              'NiceMultipleOutputs': None,
            },
          },
          'util': {
            Default: 'hadoop-mapreduce-client',
            'HostUtil': None,
          },
        },
        'mrunit': 'mrunit',
        'security': 'hadoop-common',
        'util': 'hadoop-common',
      },
      'hive': {
        'jdbc': 'hive-jdbc',
        'service': 'hive-cli'
      },
      'hcatalog': 'hcatalog',
      'http': {
        Default: 'apache-httpclient',
        'message': 'commons-httpclient',
        'client': 'commons-httpclient',
        'entity': 'commons-httpclient',
        'impl': {
          Default: 'apache-httpclient',
        },
      },
      'lucene': {
        Default: 'lucene-analyzers-common',
        'analysis': {
          Default: 'lucene-analyzers-common',
          'cn': 'lucene-analyzers-smartcn',
          'ja': 'lucene-analyzers-kuromoji',
          'icu': 'lucene-analyzers-icu',
        },
      },
      'parquet': 'parquet',
      'sanselan': 'sanselan',
      'spark': 'spark',
      'thrift': 'thrift',
      'zookeeper': 'zookeeper',
    },
    'atilika': {
      'kuromoji': 'kuromoji',
    },
    'bson': 'mongodb',
    'bytedeco': 'tesseract-javacpp',
    'clapper': {
      'argot': 'argot',
      'classutil': 'classutil',
    },
    'codehaus': {
      'jackson': 'jackson',
    },
    'elasticsearch': {
      Default: 'elasticsearch',
      'hadoop': 'elasticsearch-hadoop-mr',
    },
    'fusesource': {
      'scalate': 'scalate',
    },
    'geonames': {
      'Admin1Cities': 'admin1cities',
      'NameTranslation': 'NameTranslation',
      'Cities15000': 'cities15000',
      'CountryInfo': 'countryinfo'
    },
    'geotools': {
      'geometry': 'gt-main',
      'geojson': 'gt-geojson',
      Default: 'gt-shapefile'
    },
    'hamcrest': 'junit',
    'I0Itec': {
      'zkclient': 'kafka',
    },
    'jboss': {
      'netty': 'netty',
    },
    'jets3t': 'jets3t',
    'jh': 'sizer',
    'joda': {
      'time': 'joda-time',
    },
    'json4s': 'json4s',
    'jsoup': 'jsoup',
    'junit': 'junit',
    'mockito': 'mockito',
    'mortbay': {
      'jetty': 'jetty',
      'resource': 'jetty',
      'thread': 'jetty',
      'util': 'jetty-util',
    },
    'mozilla': {
      'javascript': 'rhino',
    },
    'objectweb': {
      'asm': 'asm'
    },
    'openid4java': 'openid-client',

    'opencv': {
      'HaarCascadeFrontalFaceAlt': 'haarcascade_frontalface_alt',
      Default: 'opencv'
    },
    'opengis': 'gt-shapefile',
    'openimaj': 'openimaj',
    'parboiled': 'parboiled-scala',
    'postgresql': {
      'core': 'postgresql',
    },
    'reflections': 'reflections',
    'scala_tools': {
      'time': 'scalaj-time',
    },
    'scalacheck': 'scalacheck',
    'slf4j': 'slf4j-all',
    'slf4s': 'slf4s',
    'specs': 'specs',
    'specs2': 'specs2',
    'tartarus': {
      'snowball': 'lucene-analyzers-common',
    },
    'xerial': {
      'snappy': 'snappy-java',
    },
    'xml': Skip,
  },
  'redis': {
    'clients': {
      'jedis': 'jedis',
    },
  },
  'scala': Skip,
  'scalaj': {
    'http': 'scalaj-http',
  },
  'scalax': 'scala-io',
  'scopt': 'scopt',
  'storm': {
    'kafka': 'storm-kafka',
  },
  'sun': {
    'reflect': Skip,
    'misc': {
      'BASE64Decoder': Skip,  # NOTE(ryan): I can't find where this lives
      'BASE64Encoder': Skip,  # NOTE(ryan): I can't find where this lives
    },
  },
  'tools': {
    'nsc': 'scala-io',
  },
  'zemberek': 'zemberek',
}
