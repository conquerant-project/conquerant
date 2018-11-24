(ns conquerant.core
  (:refer-clojure :exclude [await promise])
  (:require [clojure.walk :refer [prewalk-replace]]
            [promesa.core :as p]))

(defn- async-fn [fn]
  (for [[argv & body] (rest fn)]
    (list argv (cons `p/do* body))))

(defmacro async [expr]
  (if (and (list? expr) (seq expr))
    (let [expr (->> expr
                    macroexpand
                    (prewalk-replace {'let `p/alet}))
          type (first expr)]
      (condp = type
        'fn* `(fn ~@(async-fn expr))
        `def `(defn ~(second expr)
                ~@(async-fn (last expr)))
        `(p/do* ~expr)))
    `(p/do* ~expr)))

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
     (println z))))
