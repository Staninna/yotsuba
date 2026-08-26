# ADR-0003 — One OkHttp client, shared, except where it must not be

**Status:** accepted

## Context

Coil, Media3 and the 4chan API each default to their own HTTP stack: three
connection pools, three dispatchers, three sets of timeouts, and a cookie jar
that only one of them sees.

## Decision

One `OkHttpClient` is provided by Hilt and shared with Coil and Media3. It
carries the cookie jar, the rate limiter and the cache-policy interceptors. Its
own `Cache` is small (10 MB) and holds API JSON only; Coil keeps a separate
~200 MB `diskCache` for images.

The **updater is deliberately excluded** and builds its own client. The shared
one carries a 4chan rate limiter and a JSON cache, neither of which has any
business on `api.github.com`.

## Consequences

- Rate limiting is applied as a network interceptor, so cache hits and
  `only-if-cached` requests are never throttled.
- Anything new that talks to a non-4chan host should ask whether the shared
  client's interceptors make sense for it. Usually they do not.
