# conquerant

**async/await for Clojure**

[![Build Status](https://travis-ci.com/divs1210/conquerant.svg?branch=master)](https://travis-ci.com/divs1210/conquerant) [![codecov](https://codecov.io/gh/divs1210/conquerant/branch/master/graph/badge.svg)](https://codecov.io/gh/divs1210/conquerant)

A lightweight Clojure wrapper around `ForkJoinPool` and `CompletableFuture`
for concurrency that is simple *and* easy.

## Why

`core.async` is very powerful, but quite low-level by design
- it allows for a very flexible style of writing concurrent code
- but has a huge surface area, and can be daunting to learn
- we don't always want this kind of power
- doesn't allow using [custom threadpools](https://dev.clojure.org/jira/browse/ASYNC-94)
- breaks on [function boundaries](https://github.com/clojure/core.async/wiki/Go-Block-Best-Practices)
- [breaks emacs](https://github.com/clojure-emacs/cider/issues/1827)

## Usage

**`[conquerant "0.2.1"]`**

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
  - gets value/error out of callback
  - returns a `CompletableFuture`

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
    - any other expression, returning a `CompletableFuture`
    ```clojure
    @(async [1 2]) ;; => [1 2]
    ```
  - `conquerant.internals/*executor*` is bound to the common `ForkJoinPool` pool by default

- **`await`**
  - can only be used in `async` `let` blocks
    - normal `let` block anywhere inside an `async` block
    - every `let` block with a call to `await` returns a `CallableFuture`
    - recursively unwraps `CallableFuture`s
    ```clojure
    (async (let [a (await (async (async (async :a))))]
             (println (= :a a))))
    ;; => true
    ```
    - works across function boundaries

## License

Copyright © 2018 Divyansh Prakash

Distributed under the Eclipse Public License either version 1.0.
