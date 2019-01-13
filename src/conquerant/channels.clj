(ns conquerant.channels
  (:require [conquerant.core :as c]
            [conquerant.internals :as ci])
  (:import [java.util.concurrent ArrayBlockingQueue BlockingQueue]))

(defn ^BlockingQueue chan
  "Returns a channel with the given capacity (or 1)."
  ([]
   (chan 1))
  ([capacity]
   (ArrayBlockingQueue. capacity)))

(defn take!
  "Returns a `c/promise` that will be completed
  with the value received from ch."
  [^BlockingQueue ch]
  (c/promise [resolve]
    (ci/schedule
     #(resolve (or (.poll ch)
                   (take! ch))))))

(defn put!
  "Returns a `c/promise` that will resolve to
  true once x has been put on ch."
  [^BlockingQueue ch x]
  (c/promise [resolve]
    (ci/schedule
     #(resolve (or (.offer ch x)
                   (put! ch x))))))

(defn alts!
  "Returns a `c/promise` that will resolve to [ch x],
  where ch is the first chan out of chans to give a value,
  and x is the value received from ch."
  [chans]
  (let [[ch] chans]
    (c/promise [resolve]
      (ci/schedule
       #(let [done? (volatile! false)]
          (doseq [^BlockingQueue ch chans
                  :while (not @done?)]
            (when-let [x (.poll ch)]
              (resolve [ch x])
              (vreset! done? true)))
          (when-not @done?
            (resolve (alts! chans))))))))

(defn timeout
  "Returns a `chan` that will eventually have
  timeout-val (or `::timeout`) after timeout-ms."
  ([timeout-ms]
   (timeout timeout-ms ::timeout))
  ([timeout-ms timeout-val]
   (let [ch (chan)
         pr (c/promise)]
     (c/async (let [res (c/await pr timeout-ms timeout-val)]
                (put! ch res)))
     ch)))
