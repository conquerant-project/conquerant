# conquerant

**async/await for Clojure**

[![Clojars Project](https://img.shields.io/clojars/v/conquerant.svg)](https://clojars.org/conquerant) [![Build Status](https://travis-ci.com/divs1210/conquerant.svg?branch=master)](https://travis-ci.com/divs1210/conquerant) [![codecov](https://codecov.io/gh/divs1210/conquerant/branch/master/graph/badge.svg)](https://codecov.io/gh/divs1210/conquerant)

A lightweight Clojure wrapper around `ForkJoinPool` and `CompletableFuture`
for concurrency that is simple *and* easy.

## Why

**`core.async`**
- is very powerful, but quite low-level by design
- and the source is hard to follow
- we don't always want this kind of power
- doesn't allow using [custom threadpools](https://dev.clojure.org/jira/browse/ASYNC-94)
- breaks on [function boundaries](https://github.com/clojure/core.async/wiki/Go-Block-Best-Practices)
- [breaks emacs](https://github.com/clojure-emacs/cider/issues/1827)

## Usage

```clojure
;; Async HTTP Exaxmple
;; ===================
(refer-clojure :exclude '[await promise])
(require '[clj-http.client :as client]
         '[conquerant.core :refer [async await promise]])

(def url "https://gist.githubusercontent.com/divs1210/2ce84f3707b785a76d225d23f18c4904/raw/2dedab13201a8a8a2c91c3800040c84b70fef2e2/data.edn")

(defn fetch [url]
  (promise [resolve]
    (client/get url
                {:async? true}
                (fn [response]
                  (resolve [response nil]))
                (fn [error]
                  (resolve [nil error])))))

(async
  (let [[response error] (await (fetch url))]
    (if error
      (println "Error:" (.getMessage error))
      (println "Response Body:" (:body response)))))

(println "fetching asynchronously...")
;; => fetching asynchronously...
;; => Response Body: {:result 1}
```

- **`promise`**
  - gets value out of callback
  - returns a `CompletableFuture`
  - can be resolved from outside via `complete`
  - can be `deref`ed: `@(promise [resolve] (resolve :hi))`
  - can run on a custom `ExecutorService` using `with-async-executor`

- **`async`**
  - can wrap
    - `defn` and `fn` forms - supports variadic versions
    ```clojure
    (async (defn f
             ([a]
               (inc a))
             ([a b]
               (* a b))))
    ```
    - any other expression, returning a `CompletableFuture` (`promise`)
    ```clojure
    @(async [1 2]) ;; => [1 2]
    ```
  - can run on a custom `ExecutorService` using `with-async-executor`

- **`await`**
  - can only be used in `async` `let` bindings
    - normal `let` block anywhere inside an `async` block
    - every `let` block with a call to `await` returns a `CompletableFuture`
  - works across function boundaries
  - can timeout like `deref`: `(await p 1000 :timeout)`

- **`channels`**
  - supports `core.async`-like [channel operations](src/conquerant/channels.clj)!

## Walkthrough

Clone this repo, fire up a REPL, and walk through the [conquerant walkthrough](examples/walkthrough.clj).

## License

Copyright © 2018 Divyansh Prakash

Distributed under the Eclipse Public License either version 1.0.
