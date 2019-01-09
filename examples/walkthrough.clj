;; This walkthrough introduces the core concepts of conquerant.
;; It is inspired by and roughly mimics the core.async walkthrough.

;; The conquerant.core namespace contains the public API.

(require '[conquerant.core :as c])


;;;; Promises

;; Execution is synchronized by waiting on promises,
;; which are instances of `java.util.concurrent.CompletableFuture`.

;; Use `c/promise` to make an empty promise:

(c/promise)

;; Use `c/complete` to deliver a promise:

(c/complete (c/promise) :done)


;;;; Blocking Reads

;; In ordinary threads, use `deref` or `@` to read the value in a promise:

(let [p (c/promise)]
  (c/complete p "hello")
  (assert (= "hello" @p)))

;; If we try to deref an unfulfilled promise, we will block the main thread.


;;;; Async / Await

;; The `c/async` macro asynchronously executes its body on a special pool
;; of threads. Reads that normally block will pause execution instead.

;; This mechanism encapsulates the inversion of control that is external
;; in event/callback systems.

;; Inside `c/async` blocks, we use `c/await` instead of `deref`.
;; Here we convert our prior example to use async/await:

(let [p (c/promise)]
  (c/async (let [v (c/await p)]
             (assert (= "hello" v))))
  (c/complete p "hello"))

;; `c/promise`s can be delivered from inside:

(let [p (c/promise [resolve]
          (resolve "hello"))]
  (c/async (let [v (c/await p)]
             (assert (= "hello" v)))))

;; `c/async` also returns a promise:

(let [p (c/async "hello")]
  (c/async (let [v (c/await p)]
             (assert (= "hello" v)))))


;;;; Timeouts

;; `c/await` can timeout like deref:

(c/async (let [p (c/promise)
               v (c/await p 1000 :not-done)]
           (assert (= :not-done v))))


;;;; Custom Threadpools

;; conquerant supports switching the threadpool
;; on which async blocks and promises run:

(letfn [(info [id]
          (locking *out*
            (println id "running on thread:"
                     (.getName (Thread/currentThread)))))]
  (c/async (info "async 1"))
  (c/promise [_]
    (info "promise 1"))

  (c/with-async-executor (java.util.concurrent.ForkJoinPool. 1)
    (c/async (info "async 2"))
    (c/promise [_]
      (info "promise 2"))))
