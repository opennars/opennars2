(ns narjure.core
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [immutant.scheduling :refer :all]
    [narjure.memory-management
     [concept-manager :refer [concept-manager c-bag max-concepts]]
     [event-buffer :refer [event-buffer e-bag max-events]]
     [task-dispatcher :refer [task-dispatcher]]]
    [narjure.general-inference
     [concept-selector :refer [concept-selector]]
     [event-selector :refer [event-selector]]
     [general-inferencer :refer [general-inferencer]]]
    [narjure.perception-action
     [operator-executor :refer [operator-executor]]
     [sentence-parser :refer [sentence-parser]]
     [task-creator :refer [task-creator]]]
    [taoensso.timbre :refer [info set-level!]]
    [narjure.bag :as b])
  (:refer-clojure :exclude [promise await])
  (:import (ch.qos.logback.classic Level)
           (org.slf4j LoggerFactory)
           (java.util.concurrent TimeUnit))
  (:gen-class))

(def inference-tick-interval 25)
(def system-tick-interval 1000)

(defn inference-tick []
  (cast! (whereis :concept-selector) [:inference-tick-msg])
  (cast! (whereis :event-selector) [:inference-tick-msg]))

(defn system-tick []
  (cast! (whereis :task-creator) [:system-time-tick-msg]))

(defn prn-ok [msg] (info (format "\t[OK] %s" msg)))

(defn start-timers []
  (info "Initialising system timers...")
  (schedule inference-tick {:in    inference-tick-interval
                            :every inference-tick-interval})
  (prn-ok :inference-timer)

  (schedule system-tick {:every system-tick-interval})
  (prn-ok :system-timer)

  (info "System timer initialisation complete."))

(defn disable-third-party-loggers []
  (doseq [logger ["co.paralleluniverse.actors.JMXActorMonitor"
                  "org.quartz.core.QuartzScheduler"
                  "co.paralleluniverse.actors.LocalActorRegistry"
                  "co.paralleluniverse.actors.ActorRegistry"
                  "org.projectodd.wunderboss.scheduling.Scheduling"]]
    (.setLevel (LoggerFactory/getLogger logger) Level/OFF)))

(defn setup-logging []
  (set-level! :debug)
  (disable-third-party-loggers))

; supervisor test code
(def child-specs
  #(list
    ["1" :permanent 5 5 :sec 100 (concept-selector)]
    ["2" :permanent 5 5 :sec 100 (event-selector)]
    ["3" :permanent 5 5 :sec 100 (event-buffer)]
    ["4" :permanent 5 5 :sec 100 (concept-manager)]
    ["5" :permanent 5 5 :sec 100 (general-inferencer)]
    ["6" :permanent 5 5 :sec 100 (operator-executor)]
    ["7" :permanent 5 5 :sec 100 (sentence-parser)]
    ["8" :permanent 5 5 :sec 100 (task-creator)]
    ["9" :permanent 5 5 :sec 100 (task-dispatcher)]))

(def sup (atom '()))

(defn run []
  (setup-logging)
  (info "NARS initialising...")
  (start-timers)

  ; reset global bags
  (reset! c-bag (b/default-bag max-concepts))
  (reset! e-bag (b/default-bag max-events))

  (reset! sup (spawn (supervisor :all-for-one child-specs)))

  ; update user with status
  (info "NARS initialised."))

(defn shutdown []
  (info "Shutting down actors...")

  ; cancel schedulers
  (stop)

  (shutdown! @sup)
  (join @sup)

  (info "System shutdown complete."))

; call main function
(run)
