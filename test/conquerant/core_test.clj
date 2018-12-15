(ns conquerant.core-test
  (:refer-clojure :exclude [await promise])
  (:require [clojure.test :refer :all]
            [conquerant.core :refer :all]))

(deftest core-tests
  (testing "async block"
    (let [x (async :hello)]
      (is (promise? x))
      (is (= :hello @x)))

    (is (= 1 @(async (do 1)))
        "async block works with do"))

  (testing "async fn"
    (let [twice (async (fn [x]
                         (* 2 x)))]
      (is (fn? twice))
      (is (promise? (twice 3)))
      (is (= 6 @(twice 3)))))

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
                  s3 (await s2)]
              (is (= @s2 s3))))

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
      (let [p (promise [_])
            x (await p 1000 5)
            y x]
        (is (= 5 y)
            "await can timeout like deref"))))

  (testing "promise"
    (let [p (promise [resolve]
              (resolve :hi))]
      (is (= :hi @p))

      @(async (let [res (await p)]
                (is (= :hi @p)))))

    (let [p (promise [_])]
      (complete p 1)
      @(async (let [x (await p)]
                (is (= 1 x))))
      (is (= 1 @p))))

  (testing "crossing fn boundaries"
    (async
     (let [ps (for [i (range 5)]
                (promise [resolve]
                  (resolve i)))]
       (is (= (range 1 6)
              (map #(deref (let [i (await %)]
                             (inc i)))
                   ps)))))))


(deftest perf-tests
  (testing "> 2 million concurrent tasks"
    @(async (let [N 1000000
                  rand-ints (repeatedly N #(rand-int 100))
                  int-promises (doall (map #(async %) rand-ints))
                  sum (await (reduce (fn [acc x]
                                       (let [acc (await acc)
                                             x (await x)]
                                         (+ acc x)))
                                     (async 0)
                                     int-promises))]
              (is (= (apply + rand-ints) sum))))))
