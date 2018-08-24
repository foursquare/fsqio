# This is a basic VCL configuration file for varnish.  See the vcl(7)
# man page for details on VCL syntax and semantics.
# 
# Default backend definition.  Set this to point to your content
# server.
# 
backend default {
  .host = "localhost";
  .port = "81";
  .between_bytes_timeout = 600s;
  .connect_timeout = 600s;
  .first_byte_timeout = 600s;
}

# Force all artifacts to be cached for 7 days. 
# We'll invalidate out-of-band if we need to. 
sub vcl_fetch {
  set beresp.ttl = 7d;
  set beresp.do_stream = true;
}

# Force all artifacts to be cached for 7 days.
# We'll invalidate out-of-band if we need to.
sub vcl_fetch {
  if (beresp.status == 404 || beresp.status == 503 || beresp.status == 500) {
    set beresp.ttl = 0s;
  } else {
    set beresp.ttl = 7d;
    set beresp.do_stream = true;
  }
}
