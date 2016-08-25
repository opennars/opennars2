(ns examples.interface
  (:require [aleph.tcp :as tcp]
            [examples.ircbot :refer [concepts concept]]
            [narjure.global-atoms :refer :all]
            [narjure.core :as nar]
            [narjure.sensorimotor :refer :all]
            [narjure.narsese :refer [parse2]]
            [narjure.debug-util :refer :all]
            [clojure.string :as cstr]
            [clojure.core.async :refer [>! <! >!! <!! chan go]])
  (:gen-class))

; The help dialogue for the server
(def HELP "Client Querys:
!new_op {string} - name a new operation
!n {string} - input narsese.
!c {string} - show concept.
!cs - show concepts.
!r - reset NARS.

Server Response:
*answ {question} {answer}
*invalid
*confirmed
*online
*offline

Server Commands:
?say &args - execute operation

Client Response:
$op_name $t/f$ & ret
$invalid")

; ID & Dividers
(defn newid [] (str (java.util.UUID/randomUUID)))
(def IN ":>:")
(def OUT ":<:")
(def CONFIRMED "confirmed")
(def INVALID "invalid")

; Support functions
(defn exists?
  "Tests: v equals some item in l?"
  [v l]
  (some #(= v %) l))

(defn all?
  "[l]:   Tests: All items in l are true.
   [f l]: Tests: All items in l are evaluated true by f?"
  ([l]
  (all? (fn [x] x) l))
  ([f l]
  (not (some (complement f) l))))

; Communication
(def writer (chan))
(defn sendCMD
  "Sends string over TCP, returns boolean success."
  ([op]
  (sendCMD (newid) op))
  ([id op]
  (let [msg (str id OUT op)]
    (println (str "Sending: " msg))
    (go (>! writer msg))))
  ([id op & args]
  (let [msg (str id OUT op OUT (cstr/join OUT args))]
    (println (str "Sending: " msg))
    (go (>! writer msg)))))

(defn confirm
  "Quick function to send confirmation or error as a response given boolean input."
  [id success]
  (do
    (if success
      (sendCMD id CONFIRMED)
      (sendCMD id INVALID))
  success))

; Automatic true/false
(def error #(confirm % false))
(def conf  #(confirm % true))

; Narsese Input
(defn parse-narsese
  "Input the received Narsese into the system."
  [string]
  (try
    (let [statement (parse2 string)]
      (nars-input-narsese string)
      (println (str "NARS hears " string))
      true)
    (catch Exception e false)))

(defn valid-narsese
  "Checks if input narsese is valid."
  [string]
  (try
    (do (parse2 string) true)
    (catch Exception e false)))

; Asyncronous listening function
(def waiting (atom {}))
(defn new_op_template
  "Used as the template to define new operations in narsee over the server."
  [op_name args operationgoal]
  ; First, create the key we will use for this particular call
  (let [id (newid)
        comb (into args operationgoal)]
  ; First, add a channel for yourself
  (swap! waiting conj [id (chan)])
  ; Then, send the message requesting an answer
  (apply sendCMD (into [id op_name] comb))
  ; Then wait for a reply
  (let [[tf & extra] (<!! (get waiting id))]
  ; When done, delete yourself from waiting
  (swap! waiting dissoc id)
  ; Then, process extra as narsee, and return true or false
  (if (exists? tf ["True" "true" "t" "T" "1" "1." "1.0"])
      (confirm id (all? (map parse-narsese extra)))
      false))))

(defn new_op
  "Register a new operation."
  [op_name]
  (nars-register-operation (partial new_op_template op_name)))

(defn answer-question
  "Specifically handles answers."
  [id tf & extra]
  (>!! (get waiting id) (into [tf] extra))) ; put [tf & extra] onto channel in waiting at key id

; Copied from ircbot
(defn concept
  "Show a concept"
  [concept-str]
  (try
    (let [statement (parse2 (str concept-str "."))]
      (dissoc
        (first (narjure.bag/get-by-id @c-bag (:statement statement)))
        :ref))
    (catch Exception e (str "Invalid narsese " concept-str))))

(defn concepts
  "Show all the concepts"
  []
  (:priority-index @c-bag))

; Read Loop
(defn parse-in
  "Prints the string as received and splits it in two."
  [string] (do
    (println (str "Received: " string))
    (map cstr/trim (cstr/split string (re-pattern IN)))))

(defn process-in
  [id op & args]
  (case op
    "new-op"   (confirm id (new_op (get args 0)))
    "answer"   (confirm id (apply answer-question (into [id] args)))
    "input"    (confirm id (all? (map parse-narsese args)))
    "concept"  (try (sendCMD id "concept" (concept (get args 0))) (catch Exception e (error)))
    "concepts" (sendCMD id "concepts" (concepts))
    "help"     (sendCMD id (str HELP))
    (error id)))

(def reader (chan))
(defn readCMD
  "The main reading loop."
  []
  (do
    (let [input (<!! reader)]
      (println (str "Received: " input))
      (apply process-in (parse-in input))
    (recur))))

; Startup
(defn setup-nars
  "Registers the operation and answer handler"
  []
  (do
  (nars-register-operation 'op_say (fn [args operationgoal]
                                      (let [allargs (conj args operationgoal)
                                            total   (into [(newid) "say"] allargs)]
                                      (apply sendCMD total))))
  (nars-register-answer-handler (fn [task solution]
                                  (let [taskn (str (narsese-print (:statement task)) "?")
                                        soln  (str (task-to-narsese solution))]
                                    (sendCMD (newid) "answer" taskn soln))))
  ))

(defn -test [] (do
  (>!! reader (str 1 IN "input" IN "<a --> b>."))
  (>!! reader (str 2 IN "input" IN "<b --> c>."))
  (>!! reader (str 3 IN "input" IN "<a --> c>?"))
  (loop [] (println (<!! writer)) (recur))
  ))

(defn -main [& args] (do
  ;(println "Connecting...")
  ;(tcp/start-server echo-handler {:port 10001})
  (setup-nars)
  (go (readCMD))
  (-test)))

  ;(if (not (exists? "--nogui" args))
  ;  (lense/-main)
  ;  (set-fast-speed)