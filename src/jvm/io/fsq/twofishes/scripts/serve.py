#!/usr/bin/python

import os
import sys
import locale
from optparse import OptionParser

usage = "usage: %prog [options] hfile_directory"
parser = OptionParser(usage = usage)
parser.add_option("--host", dest="host",  default="0.0.0.0", type='string',
  help="host")
parser.add_option("-p", "--port", dest="port",  default=8080, type='int',
  help="port")
parser.add_option("--warmup", dest="warmup",  default=False, action='store_true',
  help="warmup service, increases startup time")
parser.add_option("--preload", dest="preload",  default=False, action='store_true',
  help="preload index to prevent coldstart, increases startup time")
parser.add_option("--nopreload", dest="preload",  default=False, action='store_false',
  help="don't preload index to prevent coldstart, decrease startup time, increases intial latency")
parser.add_option("-c", "--console", dest="console",  default=False, action='store_true',
  help="console, not server")
parser.add_option("--hotfix_basepath", dest="hotfix", default="", type='string',
  help="path to hotfixes")
parser.add_option("--enable_private_endpoints", dest="enablePrivate", default=False, action='store_true',
  help="enable private endpoints on server")
parser.add_option("--vm_map_count", dest="vm_map_count", type='int', help="port")


(options, args) = parser.parse_args()

if len(args) != 1:
  parser.print_usage()
  sys.exit(1)

if (locale.getdefaultlocale()[1] != 'UTF-8' and
    locale.getdefaultlocale()[1] != 'UTF8'):
  print "locale is not UTF-8, unsure if this will work"
  print "see: http://perlgeek.de/en/article/set-up-a-clean-utf8-environment for details"
  sys.exit(1)

basepath = os.path.abspath(args[0])

goal = 'run'
if options.console:
  goal = 'repl'

command_args = [
  '--preload', options.preload,
  '--warmup', options.warmup,
  '--enable_private_endpoints', options.enablePrivate,
  '--host', options.host,
  '--port', options.port,
  '--hfile_basepath', basepath
]

if (len(options.hotfix) > 0):
  command_args += ['--hotfix_basepath', options.hotfix]

if options.vm_map_count:
  command_args += ['--vm_map_count', options.vm_map_count]

cmd = './pants %s src/jvm/io/fsq/twofishes/server:server-bin %s' % (
  goal,
  ' '.join(['--jvm-run-jvm-program-args=%s' % (a) for a in command_args])
)

print(cmd)
os.system(cmd)

