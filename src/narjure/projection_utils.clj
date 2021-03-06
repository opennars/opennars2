(ns narjure.projection-utils
  (:require
    [nal.deriver
     [projection-eternalization :refer [project-eternalize-to]]]
    [narjure
     [global-atoms :refer :all]]))


(defn max-statement-confidence-projected-to-now
  "Return the element of maximum statement confidence when projected to current moment."
  [state task-type event]
  (let [fil (filter (fn [z] (and (= (:task-type (:task (second z))) task-type)
                                 (or (not event)
                                     (not= (:occurrence (:task (second z))) :eternal))
                                 (= (:statement (:task (second z))) (:id state))))
                    (:elements-map (:tasks state)))]
    (if (not= fil [])
      (project-eternalize-to @nars-time
                             (:task (second (apply max-key
                                                   (fn [y]
                                                     (second (:truth
                                                               (project-eternalize-to
                                                                 @nars-time
                                                                 (:task (second y))
                                                                 @nars-time))))
                                                   fil)))
                             @nars-time)
      nil)))

(defn get-conf
  "Confidence of a task."
  [task]
  (second (:truth task)))

(defn max-statement-confidence-projected-to-now2
  "returns the task with highest projected conf
   or the first task if task t2 is nil"
  [t1 t2]
  (if t2
    (let [now @nars-time
          f project-eternalize-to]
      (f now
         (max-key
           (fn [y] (get-conf (f now y now)))
           t1 t2)
         now))
    t1))