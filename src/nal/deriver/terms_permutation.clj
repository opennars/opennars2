(ns nal.deriver.terms-permutation
  (:require [nal.deriver.utils :refer [walk]]))

;----------------------------------------------------------------------
;:order-for-all-same

(defn contains-op?
  "Checks if statement contains operators from set."
  [statement s]
  (cond
    (symbol? statement) (s statement)
    (keyword? statement) false
    :default (some (complement nil?)
                   (map #(contains-op? % s) statement))))

(defn replace-op
  "Replaces operator from the set s to op in statement"
  [statement s op]
  (walk statement (s el) op))

(defn permute-op
  "Makes permuatation of operators from s in statement."
  [statement s]
  (if (contains-op? statement s)
    (map #(replace-op statement s %) s)
    [statement]))

;equivalences, implications, conjunctions - sets of operators that are use in
; permutation for :order-for-all-same postcondititon
(def equivalences #{'<=> '</> '<|>})
(def implications #{'==> 'pred-impl '=|> 'retro-impl})
(def conjunctions #{'&& '&| 'seq-conj})

(defn generate-all-orders
  "Permutes all operators in statement with :order-for-all-same precondition."
  [{:keys [p1 p2 conclusions full-path pre] :as rule}]
  (let [{:keys [conclusion] :as c1} (first conclusions)
        statements (->> (permute-op [p1 p2 conclusion full-path pre] equivalences)
                        (mapcat (fn [st] (permute-op st conjunctions)))
                        (mapcat (fn [st] (permute-op st implications)))
                        set)]
    (map (fn [[p1 p2 c full-path pre]]
           (assoc rule :p1 p1
                       :p2 p2
                       :full-path full-path
                       :conclusions [(assoc c1 :conclusion c)]
                       :pre pre))
         statements)))

(defn order-for-all-same?
  "Return true if rule contains order-for-all-same postcondition"
  [{:keys [conclusions]}]
  (some #{:order-for-all-same} (:post (first conclusions))))

(defn check-orders [r]
  (if (order-for-all-same? r)
    (generate-all-orders r)
    [r]))