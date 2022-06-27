# If we internally fork an otherwise publicly available jar, add the public jar targets here.


jar_library(
  name = 's2',
  tags = [
    'opensource',
  ],
  jars = [
    jar(org = 'io.sgr', name = 's2-geometry-library-java', rev = '1.0.1'),
  ]
)


jar_library(
  name = 'jetty',
  jars = [
    jar(org = 'org.mortbay.jetty', name = 'jetty', rev = '6.1.25'),
  ],
  dependencies = [
    ':jetty-util',
  ],
)


jar_library(
  name = 'jetty-util',
  jars = [
    jar(org = 'org.mortbay.jetty', name = 'jetty-util', rev = '6.1.25')
  ]
)
