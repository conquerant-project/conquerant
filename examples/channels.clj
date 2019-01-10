(ns channels
  (:require [conquerant.core :as c])
  (:import [java.util.concurrent ForkJoinPool LinkedBlockingQueue TimeUnit]))

(defn chan []
  (LinkedBlockingQueue.))

(let [wait-range-ms (range 3 7)]
  (defn rand-wait-ms []
    (rand-nth wait-range-ms)))

(defonce take-put-executor
  (ForkJoinPool. 1))

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

(defn run-test []
  (let [c (chan)]
    (dotimes [i 100]
      (c/async (let [x (c/await (take! c))]
                 (println x))))
    (dotimes [i 100]
      (put! c :hi))))
