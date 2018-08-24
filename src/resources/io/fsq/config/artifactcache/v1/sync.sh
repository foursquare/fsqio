#!/bin/bash

for h in $cache_servers ; do
  echo rsyncing $h...
  rsync -vacz --rsync-path="sudo rsync" etc/ $h:/etc/ || exit 1
done

# On each cache server itself:
echo "Reloading configs..."
sudo su -c '
  /usr/sbin/nginx -t -c /etc/nginx/nginx.conf && \
  /usr/sbin/nginx -s reload -c /etc/nginx/nginx.conf \
  && /usr/bin/varnish_reload_vcl \
  && echo config reload successful \
  || echo config reload failed
'
