# conquerant

**async/await for Clojure**

A lightweight Clojure wrapper around `ForkJoinPool` and `CompletableFuture`
for concurrency that is simple *and* easy.

## Why

`core.async` is very powerful, but quite low-level by design
- it allows for a very flexible style of writing concurrent code
- but has a huge surface area, and can be daunting to learn
- we don't always want this kind of power
- doesn't allow using [custom threadpools](https://dev.clojure.org/jira/browse/ASYNC-94)
- [breaks emacs](https://github.com/clojure-emacs/cider/issues/1827)

## Usage

`[conquerant "0.1.0"]`

```clojure
(refer-clojure :exclude '[await])
(require '[conquerant.core :refer [async await]])

(async
 (defn add
   ([a] a)
   ([a b] (+ a b))))

(def test-async
  (async
   (let [sum-1 (await (add 2))
         sum-2 (await (add 2 3))]
     [sum-1 sum-2])))

@test-async ;; => [2 5]
```

- **`async`**
  - can wrap
    - `defn` and `fn` forms - supports variadic versions
    - any other expression, returning a deref-able `CompletableFuture`
  - `conquerant.internals/*executor*` is bound to the common `ForkJoinPool` pool by default

- **`await`**
  - can only be used in `async` `let` blocks
    - normal `let` block anywhere inside an `async` block
    - works across function boundaries, [unlike `core.async`](https://github.com/clojure/core.async/wiki/Go-Block-Best-Practices)

## License

Copyright © 2018 Divyansh Prakash

Distributed under the Eclipse Public License either version 1.0.
