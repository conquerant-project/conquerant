(ns conquerant.core
  (:refer-clojure :exclude [await])
  (:require [clojure.walk :refer [prewalk-replace]]
            [conquerant.internals :as ci])
  (:import [java.util.concurrent CompletableFuture]))

(defn- async-fn [fn]
  (for [[argv & body] (rest fn)]
    (list argv (cons `ci/ado body))))

(defmacro async
  "If `expr` is a `fn` or `defn` form, its body will
  run asyncronously. Otherwise, `expr` will itself
  run asyncronously, and return a `CompletableFuture`."
  [expr]
  (if (and (coll? expr) (seq expr))
    (let [expr (->> expr
                    macroexpand
                    (prewalk-replace {'let `ci/alet}))]
      (condp = (first expr)
        'fn* `(fn ~@(async-fn expr))
        `def `(defn ~(second expr)
                ~@(async-fn (last expr)))
        `do  `(ci/ado ~(rest expr))
        `(ci/ado ~expr)))
    `(ci/ado ~expr)))

(defn await
  "Use inside `async` blocks.
  Will wait for the `Completablefuture` to complete
  before evaluation resumes."
  [v]
  (throw (Exception. "await used outside async block!")))
