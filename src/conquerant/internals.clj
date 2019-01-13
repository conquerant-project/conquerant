(ns conquerant.internals
  (:require [clojure.stacktrace :as st])
  (:import [java.util.concurrent CompletableFuture CompletionStage Executor Executors ForkJoinPool ScheduledExecutorService TimeUnit]
           java.util.function.Function))

(defonce ^:dynamic *executor*
  (ForkJoinPool/commonPool))

(defonce ^:dynamic *timeout-executor*
  (Executors/newSingleThreadScheduledExecutor))

(defn complete [^CompletableFuture promise val]
  (.complete promise val))

(defn- complete-exceptionally [^CompletableFuture promise ex]
  (.completeExceptionally promise ex)
  (st/print-stack-trace ex 1))

(defn promise? [v]
  (instance? CompletionStage v))

(defn bind [^CompletionStage p callback]
  (let [binds (clojure.lang.Var/getThreadBindingFrame)
        func (reify Function
               (apply [_ v]
                 (clojure.lang.Var/resetThreadBindingFrame binds)
                 (callback v)))]
    (.thenComposeAsync p ^Function func ^Executor *executor*)))

(defn ^CompletableFuture promise* [f]
  (let [binds (clojure.lang.Var/getThreadBindingFrame)
        p (CompletableFuture.)
        reject #(complete-exceptionally p %)
        resolve (fn [res]
                  (if (promise? res)
                    (bind res #(complete p %))
                    (complete p res)))]
    (CompletableFuture/runAsync #(try
                                   (clojure.lang.Var/resetThreadBindingFrame binds)
                                   (f resolve reject)
                                   (catch Throwable e
                                     (reject e)))
                                *executor*)
    p))

(defn schedule [f]
  (.schedule ^ScheduledExecutorService *timeout-executor*
             ^Callable f
             (+ 2 (rand-int 4))
             TimeUnit/MILLISECONDS))

(defn then
  ([p f]
   (bind p (fn promise-wrap [in]
             (let [out (f in)]
               (if (promise? out)
                 out
                 (promise* (fn [resolve _]
                             (resolve out))))))))
  ([p f timeout-ms timeout-val]
   (let [promise (CompletableFuture.)
         start-ms (System/currentTimeMillis)]
     (schedule
      (fn []
        (let [now-ms (System/currentTimeMillis)
              spent-ms (- now-ms start-ms)
              pending-ms (- timeout-ms spent-ms)]
          (cond
            (.isDone ^CompletableFuture p)
            (try
              (complete promise @p)
              (catch Exception e
                (complete-exceptionally promise e)))

            (pos? pending-ms)
            (then p
                  #(complete promise %)
                  pending-ms
                  timeout-val)

            :else
            (complete promise timeout-val)))))
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
