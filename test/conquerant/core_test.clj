(ns conquerant.core-test
  (:refer-clojure :exclude [await])
  (:require [clojure.test :refer :all]
            [conquerant.core :refer [async await]]
            [conquerant.internals :as ci]))

(deftest conquerant-tests
  (testing "async block"
    (let [x (async :hello)]
      (is (ci/promise? x))
      (is (= :hello @x))))

  (testing "async fn"
    (let [twice (async (fn [x]
                         (* 2 x)))]
      (is (fn? twice))
      (is (ci/promise? (twice 3)))
      (is (= 6 @(twice 3)))))

  (testing "async defn"
    (async (defn twice [x]
             (* 2 x)))
    (is (fn? twice))
    (is (ci/promise? (twice 3)))
    (is (= 6 @(twice 3))))

  (testing "async/await"
    @(async
      (let [s1 "hello"
            s2 (async (reverse s1))
            s3 (await s2)]
        (is (= @s2 s3))))))
