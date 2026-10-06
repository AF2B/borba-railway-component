# borba-railway-component

[![CI](https://github.com/AF2B/borba-railway-component/actions/workflows/ci.yml/badge.svg)](https://github.com/AF2B/borba-railway-component/actions/workflows/ci.yml)

Railway-oriented programming for Clojure: an Either type for failure as data, steps that compose and stop at the first
failure, and one call that turns the result into an HTTP response.

A step that can fail returns a `Right` with its result or a `Left` with what went wrong. Chaining steps with `|>` runs each
one only if the previous succeeded, so the happy path reads top to bottom with no nested conditionals, and exceptions are
left for what they are for: a broken invariant, or the edge of the system (I/O, network, database).

## Install

```clojure
io.github.af2b/borba-railway-component
{:git/url "https://github.com/AF2B/borba-railway-component"
 :git/tag "v2.0.0"
 :git/sha "<the commit of the tag, printed in the release notes>"}
```

It depends only on Clojure.

## Use

```clojure
(require '[borba.railway :as rop])

(defn validate-name [user]
  (rop/validate user (comp seq :name) {:error :name-required}))

(defn check-age [user]
  (if (>= (:age user) 18)
    (rop/right user)
    (rop/failure :too-young {:age (:age user)})))

(rop/|> {:name "Ana" :age 30} validate-name check-age)
;; => #borba.railway.Right{:value {:name "Ana", :age 30}}

(rop/|> {:name "Ana" :age 12} validate-name check-age)
;; => #borba.railway.Left{:value {:age 12, :error :too-young}}

(rop/|> {:name "" :age 30} validate-name check-age)
;; => #borba.railway.Left{:value {:error :name-required}}
```

A step is any function from a value to a `Right` or a `Left`. `|>` takes expressions that evaluate to such functions, so a step
with its own arguments is a call that returns one: `(check-unique components)`.

### The error convention

The value of a `Left` is a map with an `:error` keyword and whatever context explains it, which is what `failure` builds. The
keyword is the contract: it is what a caller matches on and what `borba.railway.http` maps to a status, so it should be stable
and specific, such as `:account/insufficient-balance`, not a sentence.

### Calling code that throws

```clojure
(rop/try-right #(Integer/parseInt "42"))
;; => #borba.railway.Right{:value 42}

(rop/try-right #(Integer/parseInt "x"))
;; => #borba.railway.Left{:value {:exception-class "java.lang.NumberFormatException", :error :exception}}
```

The message of an exception can describe internals, such as a connection string, so `try-right` withholds it and keeps the
class. An `ex-info` whose data has an `:error` is a failure its author meant, and is returned as it is.

### As an HTTP response

```clojure
(require '[borba.railway.http :as http])

(http/railway->response (rop/failure :not-found {:id 7}))
;; => {:status 404, :body {:id 7, :error :not-found}}

(http/railway->response (rop/right {:id 1}) :status 201)
;; => {:status 201, :body {:id 1}}

(http/railway->response (rop/failure :rate-limited)
                        :error->status {:rate-limited 429})
;; => {:status 429, :body {:error :rate-limited}}
```

`default-error-statuses` maps `:not-found` to 404, `:validation-failed` to 422, `:conflict` to 409, `:unauthorized` to 401,
`:forbidden` to 403, `:unsupported-media-type` to 415 and `:exception` to 500. Any other error is a 400. `:error->status` takes a
map that is merged over those, or a function from the error map to a status that replaces them.

## API

| Function | What it does |
|---|---|
| `right`, `left`, `failure` | Build a success, a failure, and a failure from an `:error` code and its context |
| `right?`, `left?`, `either?` | Tell them apart |
| `lift` | Wraps a plain value as a success and leaves an Either alone |
| `bind`, `>>=`, <code>&#124;></code> | Apply steps, stopping at the first failure |
| `fmap`, `fmap-left` | Map the value of a success, or the error of a failure |
| `unwrap`, `unwrap-or`, `either` | Take the value out, with a default, or by calling a function for each side |
| `try-right` | Calls a function that may throw |
| `validate` | A success when a predicate holds, a failure otherwise |
| `borba.railway.http/railway->response` | Turns an Either into a response map |

## Design notes

- **A step must return an Either.** `bind` throws when it does not, because a step that returns a plain value would otherwise
  be passed to the next one as if it were a result, and the mistake would surface far from its cause.
- **The laws are tested, not assumed.** The test suite checks left identity, right identity and associativity of `bind`, and the
  functor laws of `fmap`, on generated values and steps.
- **`Right` and `Left` are records**, so they compare by value, print readably and can be inspected in a REPL.

## Development

```bash
make check      # lint, format, conventions, reflection, tests, coverage
make ci         # everything the pipelines enforce
```

See [CONTRIBUTING.md](CONTRIBUTING.md). The repository follows the [Borba standard](https://github.com/AF2B/borba-tooling/blob/main/docs/standard.md).

## License

[MIT](LICENSE)
