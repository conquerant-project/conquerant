(ns conquerant.core
  (:refer-clojure :exclude [await promise])
  (:require [clojure.walk :refer [prewalk-replace]]
            [conquerant.internals :as ci]))

(defn- async-fn [fn]
  (for [[argv & body] (rest fn)]
    (list argv (cons `ci/ado body))))

(defmacro async
  "If `expr` is a `fn` or `defn` form, its body will
  run asyncronously. Otherwise, `expr` will itself
  run asyncronously, and return a `CompletableFuture`.

  All async exectution occurs on the `ci/*executor*` pool,
  which is bound to the common ForkJoinPool by default."
  [expr]
  (if (and (coll? expr) (seq expr))
    (let [expr (->> expr
                    (prewalk-replace {'let* `ci/alet
                                      'let `ci/alet
                                      `let `ci/alet
                                      `await 'await})
                    macroexpand)
          type (first expr)]
      (cond
        (or (= 'fn* type)
            (= `fn type))
        `(fn ~@(async-fn expr))

        (= `def type)
        `(defn ~(second expr)
           ~@(async-fn (last expr)))

        (= `do type)
        `(ci/ado ~@(rest expr))

        :else
        `(ci/ado ~expr)))
    `(ci/ado ~expr)))

(defn await
  "Use inside `async` `let` bindings.
  The `let` block will return a `CompletableFuture`.

  (async
    (let [x (async :x)
          y (await x)]
      y))

  Will wait for the `CompletableFuture` to complete
  before evaluation resumes."
  [v]
  (throw (Exception. "await used outside async let block!")))

(defmacro promise
  "Used to get values out of callbacks.

  ex:
  ;; some fn that takes a callback
  (defn fetch [url callback] ...)

  ;; can be used as
  (let p
    (promise [resolve]
      (fetch \"http://some.service.com\"
             #(resolve %))))"
  [[resolve] & body]
  `(ci/promise* (fn [~resolve _#]
                  ~@body)))

(defn promise?
  "Returns `true` if obj is a `CompletableFuture`."
  [obj]
  (ci/promise? obj))
