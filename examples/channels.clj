(ns channels
  (:require [conquerant.core :as c]
            [conquerant.internals :as ci])
  (:import [java.util.concurrent BlockingQueue LinkedBlockingQueue TimeUnit]))

(defn int-in [min max]
  (+ min (rand-int max) 1))

(defn chan []
  (LinkedBlockingQueue.))

(defn take! [^BlockingQueue ch]
  (c/with-async-executor ci/*timeout-executor*
    (c/async
     (if-let [x (.poll ch (int-in 3 7) TimeUnit/MILLISECONDS)]
       x
       (take! ch)))))

(defn put! [^BlockingQueue ch x]
  (c/with-async-executor ci/*timeout-executor*
    (c/async
     (when-not (.offer ch x (int-in 3 7) TimeUnit/MILLISECONDS)
       (put! ch x)))))

(defn alts! [^BlockingQueue ch1 ^BlockingQueue ch2]
  (c/with-async-executor ci/*timeout-executor*
    (c/async
     (if-let [x (.poll ch1 (int-in 3 7) TimeUnit/MILLISECONDS)]
       [ch1 x]
       (alts! ch2 ch1)))))

(defn timeout! [ms]
  (let [ch (chan)
        pr (c/promise)]
    (c/async (let [_ (c/await pr ms nil)]
               (put! ch ::timeout)))
    ch))


(comment

  ;; take! and put!
  ;; ==============
  (def c (chan))

  (dotimes [i 100]
    (c/async (let [x (c/await (take! c))]
               (println x))))

  (dotimes [i 100]
    (put! c :hi))


  ;; timeout!
  ;; ========
  @(take! (timeout! 100))


  ;; alts!
  ;; =====
  (def d (chan))

  (c/async
   (let [[ch x] (c/await (alts! d (timeout! 5000)))]
     (println x))))
