(ns conquerant.channels-test
  (:require [conquerant.core :as c]
            [conquerant.channels :as cc]
            [clojure.test :refer :all]))

(deftest chan-tests
  (testing "take! and put!"
    (let [c (cc/chan)
          counter (atom 0)]
      (dotimes [i 100]
        (c/async (let [x (c/await (cc/take! c))]
                   (swap! counter + x))))
      (dotimes [_ 100]
        (cc/put! c 1))
      (Thread/sleep 1000)
      (is (= 100 @counter))))

  (testing "timeout!"
    (let [start-ms (System/currentTimeMillis)
          res @(cc/take! (cc/timeout! 1000 :some-val))
          end-ms (System/currentTimeMillis)
          wait-ms (- end-ms start-ms)]
      (is (= :some-val res))
      (is (< 900 wait-ms 1100))))

  (testing "alts!"
    (let [c (cc/chan)
          t (cc/timeout! 1000)
          [ch x] @(cc/alts! [c t])]
      (is (= ch t))
      (is (= x ::cc/timeout)))))
