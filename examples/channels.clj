(ns channels
  (:require [conquerant.core :as c])
  (:import [java.util.concurrent ForkJoinPool LinkedBlockingQueue TimeUnit]))

(defonce ^:private take-put-executor
  (ForkJoinPool. 1))

(defn- rand-wait-ms []
  (+ 3 (rand-int 2)))


(defn chan []
  (LinkedBlockingQueue.))

(defn put! [^LinkedBlockingQueue ch x]
  (c/with-async-executor take-put-executor
    (c/async
     (when-not (.offer ch x (rand-wait-ms) TimeUnit/MILLISECONDS)
       (put! ch x)))))

(defn take! [^LinkedBlockingQueue ch]
  (c/with-async-executor take-put-executor
    (c/async
     (if-let [x (.poll ch (rand-wait-ms) TimeUnit/MILLISECONDS)]
       x
       (take! ch)))))


(comment
  (def c (chan))

  (dotimes [i 100]
    (c/async (let [x (c/await (take! c))]
               (println x))))

  (dotimes [i 100]
    (put! c :hi)))
