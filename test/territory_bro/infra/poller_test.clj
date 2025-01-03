(ns territory-bro.infra.poller-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [territory-bro.infra.poller :as poller]
            [territory-bro.test.testutil :refer [thrown?]])
  (:import (ch.qos.logback.classic Logger)
           (ch.qos.logback.classic.spi LoggingEvent)
           (ch.qos.logback.core.read ListAppender)
           (java.time Duration)
           (java.util.concurrent CountDownLatch CyclicBarrier TimeUnit TimeoutException)
           (org.slf4j LoggerFactory)))

;; slow enough to be noticed as a slow test, because normally this should never be reached
(def ^Duration test-timeout (Duration/ofSeconds 5))

(defn- await-latch [^CountDownLatch latch]
  (.await latch (.toMillis test-timeout) TimeUnit/MILLISECONDS))

(defn- await-barrier [^CyclicBarrier barrier]
  (.await barrier (.toMillis test-timeout) TimeUnit/MILLISECONDS))

(deftest poller-test
  (testing "runs the task when triggered"
    (let [task-finished (CountDownLatch. 1)
          *task-count (atom 0)
          *task-thread (atom nil)
          p (poller/create (fn []
                             (swap! *task-count inc)
                             (reset! *task-thread (Thread/currentThread))
                             (.countDown task-finished)))]
      (poller/trigger! p)
      (await-latch task-finished)
      (poller/shutdown! p)

      (is (= 1 @*task-count) "task count")
      (is (instance? Thread @*task-thread))
      (is (not= (Thread/currentThread) @*task-thread)
          "runs the task in a background thread")))

  (testing "reruns when triggered after task started"
    (let [one-task-started (CountDownLatch. 1)
          two-tasks-started (CountDownLatch. 2)
          *task-count (atom 0)
          p (poller/create (fn []
                             (swap! *task-count inc)
                             (.countDown one-task-started)
                             (.countDown two-tasks-started)))]
      (poller/trigger! p)
      (await-latch one-task-started)
      (poller/trigger! p)
      (await-latch two-tasks-started)
      (poller/shutdown! p)

      (is (= 2 @*task-count) "task count")))

  (testing "reruns at most once when triggered after task started many times"
    (let [one-task-started (CountDownLatch. 1)
          many-tasks-triggered (CountDownLatch. 1)
          two-tasks-finished (CountDownLatch. 2)
          *task-count (atom 0)
          p (poller/create (fn []
                             (swap! *task-count inc)
                             (.countDown one-task-started)
                             (await-latch many-tasks-triggered)
                             (.countDown two-tasks-finished)))]
      (poller/trigger! p)
      (await-latch one-task-started)
      (poller/trigger! p)
      (poller/trigger! p)
      (poller/trigger! p)
      (poller/trigger! p)
      (.countDown many-tasks-triggered)
      (await-latch two-tasks-finished)
      (poller/shutdown! p)

      (is (= 2 @*task-count) "task count")))

  (testing "reruns many times when triggered after task finished"
    (let [task-finished (CyclicBarrier. 2)
          *task-count (atom 0)
          p (poller/create (fn []
                             (swap! *task-count inc)
                             (await-barrier task-finished)))]
      (poller/trigger! p)
      (await-barrier task-finished)
      (poller/trigger! p)
      (await-barrier task-finished)
      (poller/trigger! p)
      (await-barrier task-finished)
      (poller/shutdown! p)

      (is (= 3 @*task-count) "task count")))

  (testing "runs only a single task at a time"
    (let [*concurrent-tasks (atom 0)
          *max-concurrent-tasks (atom 0)
          *task-count (atom 0)
          timeout (+ 1000 (System/currentTimeMillis))
          p (poller/create (fn []
                             (swap! *concurrent-tasks inc)
                             (swap! *max-concurrent-tasks #(max % @*concurrent-tasks))
                             (swap! *task-count inc)
                             (Thread/yield)
                             (swap! *concurrent-tasks dec)))]
      (while (and (> 10 @*task-count)
                  (> timeout (System/currentTimeMillis)))
        (poller/trigger! p))
      (poller/shutdown! p)

      (is (< 1 @*task-count) "task count")
      (is (= 1 @*max-concurrent-tasks) "max concurrent tasks")))

  (testing "logs uncaught exceptions"
    (let [appender (doto (ListAppender.)
                     (.start))
          logger (doto ^Logger (LoggerFactory/getLogger "territory-bro.infra.executors")
                   (.addAppender appender))
          p (poller/create (fn []
                             (throw (RuntimeException. "dummy"))))]
      (poller/trigger! p)
      (poller/shutdown! p)
      (.detachAppender logger appender)

      (is (= 1 (count (.-list appender))) "log event count")
      (let [event ^LoggingEvent (first (.-list appender))]
        (is (str/starts-with? (.getMessage event) "Uncaught exception"))
        (is (= "dummy" (.getMessage (.getThrowableProxy event)))))))

  (testing "await blocks until the current task is finished"
    (let [*task-count (atom 0)
          p (poller/create (fn []
                             (Thread/yield)
                             (swap! *task-count inc)))]
      (poller/trigger! p)
      (poller/await p test-timeout)

      (is (= 1 @*task-count) "task count")
      (poller/shutdown! p)))

  (testing "await throws if the timeout is reached"
    (let [test-finished (CountDownLatch. 1)
          p (poller/create (fn []
                             (.await test-finished 1 TimeUnit/SECONDS)))]
      (poller/trigger! p)

      (is (thrown? TimeoutException
                   (poller/await p (Duration/ofMillis 0))))
      (.countDown test-finished)
      (poller/shutdown! p))))
