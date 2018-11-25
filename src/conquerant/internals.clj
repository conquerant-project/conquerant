(ns conquerant.internals
  (:refer-clojure :exclude [promise])
  (:import [java.util.concurrent CompletableFuture CompletionStage Executor ForkJoinPool]
           java.util.function.Function))

(defonce ^:dynamic *executor*
  (ForkJoinPool/commonPool))

(defn ^CompletableFuture promise* [f]
  (let [p (CompletableFuture.)
        reject #(.completeExceptionally p %)
        resolve #(.complete p %)]
    (try
      (f resolve reject)
      (catch Throwable e
        (reject e)))
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

(defn then [p f]
  (bind p (fn promise-wrap [in]
            (let [out (f in)]
              (if (promise? out)
                out
                (promise* out))))))

(defn attempt [callback]
  (promise* (fn [resolve reject]
             (let [result (callback)]
               (if (promise? result)
                 (then result resolve)
                 (resolve result))))))

(defmacro ado [& body]
  `(attempt #(do ~@body)))

(defmacro alet [bindings & body]
  (if (not-any? identity
                (for [expr (->> bindings rest (take-nth 2))]
                  (and (coll? expr)
                       (= 'await (first expr)))))
    `(let ~bindings ~@body)
    (->> (partition 2 bindings)
         reverse
         (reduce (fn [acc [l r]]
                   (if (and (coll? r)
                            (symbol? (first r))
                            (not= "." (subs (name (first r)) 0 1)))
                     (if (= 'await (first r))
                       `(bind ~(second r)
                              (fn [~l] ~acc))
                       `(let [~l ~r] ~acc))
                     `(let [~l ~r] ~acc)))
                 `(ado ~@body)))))
