(ns conquerant.core
  (:refer-clojure :exclude [await])
  (:require [clojure.walk :refer [prewalk-replace]]
            [conquerant.internals :as ci]))

(defn- async-fn [fn]
  (for [[argv & body] (rest fn)]
    (list argv (cons `ci/ado body))))

(defmacro async [expr]
  (if (and (list? expr) (seq expr))
    (let [expr (->> expr
                    macroexpand
                    (prewalk-replace {'let `ci/alet}))
          type (first expr)]
      (condp = type
        'fn* `(fn ~@(async-fn expr))
        `def `(defn ~(second expr)
                ~@(async-fn (last expr)))
        `(ci/ado ~expr)))
    `(ci/ado ~expr)))

(defn await [& args]
  (throw (Exception. "await used outside async block!")))


;; Test
;; ====
(async
 (defn f [x]
   (* 2 x)))

(async
 (defn g [x]
   (let [y (await (f x))
         z (inc y)]
     z)))
