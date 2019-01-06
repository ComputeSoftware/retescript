(require '[provisdom.retescript.core :refer :all]
         '[datascript.core :as d])

(defrules rs
          [[::r1
            [:find ?e
             :where
             [?e :a ?v]
             [(= 1 ?v)]]
            =>
            [[:db/add ?e :one true]]]

           [::r2
            [:find ?e ?v
             :where
             [?e :a ?v]
             [?e :one true]]
            =>
            [[:db/add ?e :foo :bar]]
            #_(println "R2" ?e ?v)]

           #_#_#_#_#_[::r3
                      [:find ?e ?x ?z
                       :where
                       [?e :a ?v]
                       [(+ ?x 2) ?z]
                       [(* ?v 0.3) ?x]]
                      =>
                      #_(println "R3" ?x ?z)
                      [[:db/add ?e :x ?x]]]

           [::r4
            [:find ?e1 ?v2
             :where
             [?e1 :a _]
             [_ :a ?v2]
             #_[_ :b ?v2]
             #_[(identity ?v2) ?q]
             #_[(inc ?v2) ?q]
             #_[?e1 :a ?q]]
            =>
            [[:db/add ?e1 :foo :bar]]
            #_(println "R4" ?e1 ?v2)]

           [::r5
            [:find ?e ?v ?w ?q
             :where
             [?e :a ?v]
             #_[_ :a ?w]
             [?e :b ?w]
             [?e :c ?q]
             #_[?e :a 1]
             (not [?e :c 1]
                  [?e :d 2]
                  [(> ?w 5)]
                  #_[(identity ?e) ?e])
             (or [?e :a 1]
                 (and [?e :a 2]
                      [?e :b 2]))]
            =>
            (println "R5" ?e ?v ?w ?q)
            [[:db/add ?e :foo :bar]]]

           [::r6
            [:find ?e
             :where
             [?e :b 1]
             [1 :a 1]]
            =>
            (println "R6" ?e)]

           [::q1
            [:find ?e ?v ?w ?q
             :where
             [?e :a ?v]
             [?e :b ?w]
             [?e :c ?q]
             (not [?e :c 1]
                  [?e :d 2]
                  [(> ?w 5)]
                  #_[(identity ?e) ?e])
             (or [?e :a 1]
                 (and [?e :a 2]
                      [?e :b 1]))]]])

(def s (create-session {:a {:db/cardinality :db.cardinality/many}} rs))

(comment
  (transact-datom s [:db/add 1 :a 1])
  (def s' (transact s [[:db/add 1 :a 2][:db/add 1 :a 1]]))
  (transact s' [[:db/retract 1 :a 1]])
  :end)