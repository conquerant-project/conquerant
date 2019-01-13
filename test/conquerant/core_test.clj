(ns conquerant.core-test
  (:refer-clojure :exclude [await promise])
  (:require [clojure.test :refer :all]
            [conquerant.core :as a :refer :all])
  (:import java.util.concurrent.ForkJoinPool))

(deftest core-tests
  (testing "async block"
    (let [x (async :hello)]
      (is (promise? x))
      (is (= :hello @x)))

    (is (= 1 @(async (do 1)))
        "async block works with do")

    (is (thrown? Exception
                 @(async (throw (Exception. "expected failure"))))
        "exceptions bubble up like in future"))

  (testing "async fn"
    (let [thrice (async (fn [x]
                          (* 3 x)))]
      (is (fn? thrice))
      (is (promise? (thrice 3)))
      (is (= 9 @(thrice 3)))))

  (testing "async defn"
    (async (defn twice [x]
             (* 2 x)))
    (is (fn? twice))
    (is (promise? (twice 3)))
    (is (= 6 @(twice 3))))

  (testing "await"
    (is (thrown? Exception (await (async 1)))
        "await cannot be used outside async let")

    @(async (let [s1 "hello"
                  s2 (async (reverse s1))
                  s3 (await s2)
                  s4 s3]
              (is (= @s2 s4))))

    @(async (is (promise? (let [a (async 1)
                                b (await a)]
                            b))
                "async let block with await
                 returns CallableFuture"))

    @(async (is (= 1 (let [a 1] a))
                "async let block without await
                 doesn't return CallableFuture"))

    @(async
      (let [a (await (async (async (async :a))))]
        (is (= :a a)
            "unwraps recursively")))

    @(async
      (let [p (promise)
            x (await p 1000 5)]
        (is (= 5 x)
            "await can timeout like deref")))

    @(async
      (let [p (async 1)
            x (await p 1000 :timeout)]
        (is (= 1 x)
            "timeouts are ignored if promise is complete")))

    @(async
      (let [a (async 1)
            b (a/await a)]
        (is (= 1 b)
            "await works when prefixed with namespace"))))

  (testing "promise"
    (let [p (promise [resolve]
              (resolve :hi))]
      (is (= :hi @p))

      @(async (let [res (await p)]
                (is (= :hi @p)))))

    (let [p (promise)]
      (complete p 1)
      @(async (let [x (await p)]
                (is (= 1 x))))
      (is (= 1 @p)))

    (let [p1 (async 1)
          p2 (promise [resolve]
               (resolve p1))]
      (is (= 1 @p2)
          "resolve unwraps promise")))

  (testing "crossing fn boundaries"
    (async
     (let [ps (for [i (range 5)]
                (promise [resolve]
                  (resolve i)))]
       (is (= (range 1 6)
              (map #(deref (let [i (await %)]
                             (inc i)))
                   ps)))))))


(deftest perf-test
  (testing "1 million concurrent tasks"
    (let [accumulator (atom 0)
          N 1e6]
      (dotimes [i N]
        (async (swap! accumulator inc)))
      (Thread/sleep 1000)
      (is (== N @accumulator)))))


(deftest recursion-test
  (testing "recursive asynchronous tasks don't blow the stack"
    (letfn [(async-countdown [n then-return]
              (async
               (if (> n 0)
                 (async-countdown (dec n) then-return)
                 then-return)))]
      (is (= :passed
             (deref (async-countdown 5000 :passed)
                    5000
                    :failed))))))


(deftest custom-threadpool-test
  (testing "execution on custom threadpool"
    (let [custom-pool (ForkJoinPool. 1)
          custom-pool-thread (promise [resolve]
                               (.submit ^ForkJoinPool custom-pool
                                        ^Runnable #(resolve (Thread/currentThread))))
          async-executed-on-thread (with-async-executor custom-pool
                                     (async (Thread/currentThread)))]
      (is (= @custom-pool-thread
             @async-executed-on-thread)
          "async block runs on the custom pool"))))
