(ns conquerant.internals
  (:import [java.util.concurrent CompletableFuture CompletionStage Executor Executors ExecutorService ForkJoinPool]
           java.util.function.Function))

(defonce ^:dynamic *executor*
  (ForkJoinPool/commonPool))

(defonce ^:dynamic *timeout-scheduler*
  (Executors/newSingleThreadExecutor))

(defn complete [^CompletableFuture promise val]
  (.complete promise val))

(defn ^CompletableFuture promise* [f]
  (let [p (CompletableFuture.)
        reject #(.completeExceptionally p %)
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

(defn then
  ([p f]
   (bind p (fn promise-wrap [in]
             (let [out (f in)]
               (if (promise? out)
                 out
                 (promise* out))))))
  ([p f timeout-ms timeout-val]
   (let [promise (CompletableFuture.)
         start-time-millis (System/currentTimeMillis)]
     (.submit ^ExecutorService *timeout-scheduler*
              ^Runnable #(try
                           (let [spent-ms (- (System/currentTimeMillis)
                                             start-time-millis)]
                             (complete promise (deref p
                                                      (max 0 (- timeout-ms spent-ms))
                                                      timeout-val)))
                           (catch Throwable e
                             (.completeExceptionally promise e))))
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

(in-ns 'conquerant.core)
(declare await)
(in-ns 'conquerant.internals)

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
