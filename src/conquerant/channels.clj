(ns conquerant.channels
  (:require [conquerant.core :as c]
            [conquerant.internals :as ci])
  (:import [java.util.concurrent BlockingQueue LinkedBlockingQueue]))

(defn chan []
  (LinkedBlockingQueue.))

(defn take! [^BlockingQueue ch]
  (c/promise [resolve]
    (ci/schedule
     #(resolve (or (.poll ch)
                   (take! ch))))))

(defn put! [^BlockingQueue ch x]
  (c/promise [resolve]
    (ci/schedule
     #(resolve (or (.offer ch x)
                   (put! ch x))))))

(defn alts! [[ch & chans :as all-chans]]
  (c/promise [resolve]
    (ci/schedule
     #(if-let [x (.poll ch)]
        (resolve [ch x])
        (resolve (alts! (rest (cycle all-chans))))))))

(defn timeout! [ms]
  (let [ch (chan)
        pr (c/promise)]
    (c/async (let [_ (c/await pr ms nil)]
               (put! ch ::timeout)))
    ch))
