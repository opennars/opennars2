(ns narjure.test.nal
  (:require [clojure.test :refer :all]
            [narjure.narsese :refer :all]
            [instaparse.core :refer [failure?]]
            [nal.deriver :refer :all]
            [nal.rules :as r]))

(defn conclusions
  "Create all conclusions based on two Narsese premise strings"
  ([p1 p2]                                                  ;todo probably parser should probably recognize and set occurrence 0 by default in case of :|:
   (conclusions p1 p2 0))                                   ;after this task creator assigns current time when it becomes a real task
  ([p1 p2 occurrence]
   (let [parsed-p1 (parse p1)
         parsed-p2 (parse p2)
         punctuation (:action parsed-p1)]
     (set (generate-conclusions
            (r/rules punctuation)
            (assoc parsed-p1 :occurrence occurrence)
            (assoc parsed-p2 :occurrence occurrence))))))

(defn derived                                               ;must derive single step (no tick parameter), no control dependency
  "Checks whether a certain expected conclusion is derived"
  [p1 p2 clist]
  (every? (fn [c] (let [parsed-c (parse c)]
                    (some #(and (= (:statement %) (:statement parsed-c))
                                (= (:truth %) (:truth parsed-c)))
                          (conclusions p1 p2))))
          clist))

;NAL1 testcases:

(deftest nal1-deduction
  (is (derived "<shark --> fish>."
               "<fish --> animal>."
               ["<shark --> animal>. %1.00;0.81%"])))


;(deftest nal1-abduction
;  (is (derived "<sport --> competition>."
;               "<chess --> competition>. %0.90;0.90%"
;               ["<sport --> chess>. %1.00;0.42%"
;                "<chess --> sport>. %0.90;0.45%"])))

;NAL2 testcases:

;NAL3 testcases:

;NAL4 testcases:

;NAL5 testcases:

;NAL6 testcases:

;NAL7 testcases:

;NAL8 testcases:
