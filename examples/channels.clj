(ns channels
  (:require [conquerant.core :as c])
  (:import [java.util.concurrent Executors LinkedBlockingQueue TimeUnit]))

(defonce ^:private take-put-executor
  (Executors/newSingleThreadExecutor))

(defn- rand-wait-ms []
  (+ 3 (rand-int 2)))


(defn chan []
  (LinkedBlockingQueue.))

(defn take! [^LinkedBlockingQueue ch]
  (c/with-async-executor take-put-executor
    (c/async
     (if-let [x (.poll ch (rand-wait-ms) TimeUnit/MILLISECONDS)]
       x
       (take! ch)))))

(defn put! [^LinkedBlockingQueue ch x]
  (c/with-async-executor take-put-executor
    (c/async
     (when-not (.offer ch x (rand-wait-ms) TimeUnit/MILLISECONDS)
       (put! ch x)))))

(defn alts! [^LinkedBlockingQueue ch1 ^LinkedBlockingQueue ch2]
  (c/with-async-executor take-put-executor
    (c/async
     (if-let [x (.poll ch1 (rand-wait-ms) TimeUnit/MILLISECONDS)]
       [ch1 x]
       (alts! ch2 ch1)))))


(comment "TESTS"
  (def c (chan))

  (dotimes [i 100]
    (c/async (let [x (c/await (take! c))]
               (println x))))

  (dotimes [i 100]
    (put! c :hi))


  (def d (chan))

  (def e (chan))

  ((fn loop []
     (c/async
      (let [[ch x] (c/await (alts! d e))]
        (println x)
        (loop)))))

  (put! d :d))
