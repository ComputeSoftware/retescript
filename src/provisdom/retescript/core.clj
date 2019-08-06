(ns provisdom.retescript.core
  (:require [datascript.core :as d]
            [datascript.parser :as dp]
            [datascript.db :as db]
            [clojure.set :as set]
            [clojure.pprint :refer [pprint]]))

(defn find-symbol
  [element]
  (or (:symbol element) (-> element :args first :symbol)))

(defn compile-rule
  [rule-def]
  (let [[name query _ rhs-fn] rule-def
        query-ast (try
                    (dp/parse-query query)
                    (catch Exception e
                      (throw (ex-info (.getMessage e) {:rule-def rule-def}))))
        #_#_rhs-args (->> query-ast :qfind :elements (mapv find-symbol))
        #_#_rhs-fn `(fn [~@rhs-args]
                      ~@rhs)]
    {:name     name
     :query    `'~query
     #_#_:rhs-args `'~rhs-args
     :rhs-form `'~rhs-fn
     :rhs-fn   rhs-fn
     :bindings {}}))

(defmacro defrules
  [name rules]
  (let [cr (mapv compile-rule rules)]
    `(def ~name
       ~cr)))

(defn create-session
  [schema & ruleses]
  {:rules (->> ruleses (mapcat identity) vec)
   :db    (d/empty-db schema)})

(defn update-bindings
  [{:keys [name query rhs-fn bindings] :as rule} db]
  (let [current-results (set (d/q query db))
        old-results (-> bindings keys set)
        added-results (set/difference current-results old-results)
        retracted-results (set/difference old-results current-results)
        db' (->> (select-keys bindings retracted-results)
                 (mapcat (fn [[_ ds]]
                           (->> ds
                                (map (fn [d] (assoc d 0 :db/retract))))))
                 (d/db-with db))
        [db'' added-bindings] (reduce (fn [[db bs] b]
                                        (let [rhs-tx (try
                                                       (apply rhs-fn b)
                                                       (catch Exception e
                                                         (throw (ex-info "Exception evaluating RHS"
                                                                         {:rule (select-keys rule [:name :query :rhs-form])
                                                                          :bindings b
                                                                          :ex e}))))
                                              split-unconditional (group-by #(= :db/add! (first %)) rhs-tx)
                                              conditional-tx (split-unconditional false)
                                              unconditional-tx (->> (split-unconditional true)
                                                                    (mapv #(case (count %)
                                                                             2 (second %)
                                                                             4 (assoc % 0 :db/add))))
                                              db' (try
                                                    (d/db-with db unconditional-tx)
                                                    (catch Exception e
                                                      (throw (ex-info "Exception transacting RHS result"
                                                                      {:rule (select-keys rule [:name :query :rhs-form])
                                                                       :rhs-tx unconditional-tx
                                                                       :ex e}))))
                                              tx-report (try
                                                          (d/with db' conditional-tx)
                                                          (catch Exception e
                                                            (throw (ex-info "Exception transacting RHS result"
                                                                            {:rule (select-keys rule [:name :query :rhs-form])
                                                                             :rhs-tx conditional-tx
                                                                             :ex e}))))
                                              {db'' :db-after tx-data :tx-data} tx-report]
                                          [db'' (assoc bs b (->> tx-data
                                                                 (filter (fn [{:keys [added]}] added))
                                                                 (map (fn [{:keys [e a v]}] [:db/add e a v]))
                                                                 set))]))
                                      [db' {}] added-results)
        #_#_added-bindings (->> added-results
                                (map (fn [b]
                                       (->> b
                                            (apply rhs-fn)
                                            set
                                            (vector b))))
                                (into {}))
        #_#_added-datoms (->> added-bindings
                              (mapcat val)
                              set)]
    (when (or (not-empty added-results) (not-empty retracted-results))
      (tap> {:tag ::update-bindings
             :name name
             :added-results added-results
             :added-bindings added-bindings
             :retracted-results retracted-results
             :retracted-bindings (select-keys bindings retracted-results)}))
    [(merge added-bindings (apply dissoc bindings retracted-results))
     db'']))

(defn transact
  ; TODO - check tx-data is vector of datoms?
  [session tx-data]
  (loop [{:keys [rules] :as session} (update session :db d/db-with tx-data)]
    (let [session' (reduce (fn [{:keys [db] :as rs} rule]
                             (let [[bs db'] (update-bindings rule db)]
                               (-> rs
                                   (update :rules conj (assoc rule :bindings bs))
                                   (assoc :db db'))))
                           (assoc session :rules []) rules)]
      (if (= (:db session) (:db session'))
        session
        (recur session')))))