(ns conquerant.internals
  (:require [clojure.stacktrace :as st])
  (:import [java.util.concurrent CompletableFuture CompletionStage Executor Executors ForkJoinPool]
           java.util.function.Function))

(defonce ^:dynamic *executor*
  (ForkJoinPool/commonPool))

(defonce ^:dynamic *timeout-executor*
  (Executors/newSingleThreadExecutor))

(defn complete [^CompletableFuture promise val]
  (.complete promise val))

(defn- complete-exceptionally [^CompletableFuture promise ex]
  (.completeExceptionally promise ex)
  (st/print-stack-trace ex 1))

(defn ^CompletableFuture promise* [f]
  (let [p (CompletableFuture.)
        reject #(complete-exceptionally p %)
        resolve #(complete p %)]
    (CompletableFuture/runAsync #(try
                                   (f resolve reject)
                                   (catch Throwable e
                                     (reject e)))
                                *executor*)
    p))

(defn promise? [v]
  (instance? CompletionStage v))

(defn bind [^CompletionStage p callback]
  (let [binds (clojure.lang.Var/getThreadBindingFrame)
        func (reify Function
               (apply [_ v]
                 (clojure.lang.Var/resetThreadBindingFrame binds)
                 (callback v)))]
    (.thenComposeAsync p ^Function func ^Executor *executor*)))

(defn- timeout [start-ms timeout-ms]
  (let [spent-ms (- (System/currentTimeMillis) start-ms)
        remaining-ms (max (- timeout-ms spent-ms) 0)
        wait-range-ms (+ 3 (rand-int 2))
        wait-ms (min remaining-ms wait-range-ms)
        pending-ms (- remaining-ms wait-ms)]
    [wait-ms pending-ms]))

(defn then
  ([p f]
   (bind p (fn promise-wrap [in]
             (let [out (f in)]
               (if (promise? out)
                 out
                 (promise* (fn [resolve _]
                             (resolve out))))))))
  ([p f timeout-ms timeout-val]
   (let [start-ms (System/currentTimeMillis)
         promise (CompletableFuture.)]
     (CompletableFuture/runAsync #(try
                                    (let [[wait-ms pending-ms] (timeout start-ms timeout-ms)
                                          v (deref p wait-ms ::timeout)]
                                      (if (= v ::timeout)
                                        (if (pos? pending-ms)
                                          (then p
                                                (fn [x]
                                                  (complete promise x))
                                                pending-ms
                                                timeout-val)
                                          (complete promise timeout-val))
                                        (complete promise v)))
                                    (catch Throwable e
                                      (complete-exceptionally promise e)))
                                 *timeout-executor*)
     (then promise f))))

(defn attempt [callback]
  (promise* (fn [resolve reject]
              (let [result (callback)]
                (if (promise? result)
                  (then result resolve)
                  (resolve result))))))

(defmacro ado [& body]
  `(attempt (fn []
              ~@body)))

(create-ns 'conquerant.core)
(intern 'conquerant.core 'await)

(defn await? [sym]
  (and (symbol? sym)
       (= #'conquerant.core/await (resolve sym))))

(defmacro alet [bindings & body]
  (if (not-any? identity
                (for [expr (->> bindings rest (take-nth 2))]
                  (and (coll? expr)
                       (await? (first expr)))))
    `(let ~bindings ~@body)
    (->> (partition 2 bindings)
         reverse
         (reduce (fn [acc [l r]]
                   (if (and (coll? r)
                            (symbol? (first r))
                            (not= "." (subs (name (first r)) 0 1)))
                     (if (await? (first r))
                       (let [[_ expr & timeout] r]
                         `(then ~expr
                                (fn [~l] ~acc)
                                ~@timeout))
                       `(let [~l ~r] ~acc))
                     `(let [~l ~r] ~acc)))
                 `(ado ~@body)))))
