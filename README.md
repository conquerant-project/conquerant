# conquerant

**async/await for Clojure**

A lightweight Clojure wrapper around `ForkJoinPool` and `CompletableFuture`
for concurrency that is both simple *and* easy.

## Why

- `core.async` is very powerful, but quite low-level by design
  - it allows for a very flexible style of writing concurrent code
  - but has a huge surface area, and can be daunting to learn
  - we don't always want this kind of power
- doesn't allow using custom threadpools

## Usage

```clojure
(refer-clojure :exclude '[await])
(require '[conquerant.core :refer [async await]])

(async
  (defn twice [x]
    (* 2 x)))

(def six
  @(async
    (let [y (await (twice 3))]
      (println "y:" y)
      y)))
```

### async
  - can wrap
    - `defn` and `fn` forms - supports variadic versions
    - any other expression, returning a deref-able `CompletableFuture`
  - `conquerant.internals/*executor*` is bound to ForkJoinPool pool by default

### await
  - can only be used in `async` `let` blocks

Check out the [tests](./test/conquerant/core_test.clj) for examples!

## License

Copyright © 2018 Divyansh Prakash

Distributed under the Eclipse Public License either version 1.0.
