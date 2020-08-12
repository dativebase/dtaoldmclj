(ns dtaoldm.aol
  "DativeTop Append-only Log Domain Model (DTAOLDM)."
  (:require [dtaoldm.utils :as u]))

(def has-attr "db::has")
(def lacks-attr "db::lacks")
(def is-a-attr "db::is-a")
(def being-val "being")
(def extant-pred [has-attr being-val])
(def non-existent-pred [lacks-attr being-val])
(def being-preds [extant-pred non-existent-pred])

(defn appendable->map
  [[[e a v _] _ _]]
  {e (merge
      {:db/id e}
      (cond
        (= extant-pred [a v]) {:db/extant true}
        (= non-existent-pred [a v]) {:db/extant false}
        (= is-a-attr a) {:db/type v}
        :else {(keyword "dtaoldm.domain" a) v}))})

(defn pluralize-keyword [kw] (-> kw name (str "s") keyword))

(defn aol->domain-entities
  "Given an append-only log ``aol``---a sequence of appendables (3-ary vectors)
  whose first element is an eavt quadruple---return a dict from plural domain
  entity type keywords (e.g., ``:old-instances``) to sets of domain entity maps."
  [aol]
  (->> aol
       (map appendable->map)
       (apply (partial merge-with merge))
       vals
       (filter :db/extant)
       (group-by :db/type)
       (map (juxt (comp pluralize-keyword key) val))
       (into {})))

(defn locate-start-index-reducer
  "Function to be passed to ``reduce`` in order to determine the starting index
  (int) of the suffix of a mergee changset (sequence) into a target changeset
  (sequence)"
  [{:keys [target-seen mergee-seen mergee-length index] :as agg}
   [[_ _ target-hash] [_ _ mergee-hash]]]
  (if (or (= target-hash mergee-hash)
          (some #{mergee-hash} target-seen))
    (reduced (assoc agg :start-index (- mergee-length index)))
    (let [target-seen (conj target-seen target-hash)
          other-index
          (->> mergee-seen
               reverse
               (filter (fn [[hash- _]] (some #{hash-} target-seen)))
               first
               second)]
      (if other-index
        (reduced (assoc agg :start-index (- mergee-length other-index)))
        (merge agg
               {:target-seen target-seen
                :mergee-seen (conj mergee-seen [mergee-hash index])
                :index (inc index)})))))

(defn find-changes
  "Find the suffix (possibly empty) of coll ``mergee`` that is not in coll
  ``target``. Algorithm involves processing the two colls pairwise and
  simultaneously in reverse order."
  [target mergee]
  (if-not (seq target)  ;;  target is empty, all of mergee is new
    mergee
    (let [{:keys [start-index]}
          (reduce
           locate-start-index-reducer
           {:target-seen []  ;; suffix of target hashes seen
            :mergee-seen []  ;; suffix of mergee hashes seen (elements are [hash index] 2-vecs)
            :mergee-length (count mergee)
            :index 0
            :start-index nil}
           (->> [(reverse target) (reverse mergee)]
                (apply interleave)
                (partition 2)))]
      (if start-index (drop start-index mergee) mergee))))

(def need-rebase-err
  (str "There are changes in the target AOL that are not present"
       " in the mergee AOL. Please manually rebase the mergee's"
       " changes or try again with the 'rebase' conflict"
       " resolution strategy."))

(defn get-tip-hash [aol] (->> aol last (drop 2) first))

(defn aol-append
  "Append ``eavt`` (4-ary vector) to AOL ``aol``."
  [aol eavt]
  (let [hash-of-eavt (u/hash-data eavt)]
    (conj aol [eavt hash-of-eavt
               (u/hash-string (u/get-json [(get-tip-hash aol) hash-of-eavt]))])))

(defn truncate [trunk diff-only aol]
  (if diff-only (drop (count trunk) aol) aol))

(defn- resolve-conflict [trunk _ new-from-branch strategy diff-only]
  (case strategy
    :rebase (u/just (->> new-from-branch
                         (map first)
                         (reduce aol-append trunk)
                         (truncate trunk diff-only)))
    (u/nothing need-rebase-err)))

(defn- resolve-to-branch [trunk branch _ _ diff-only]
  (u/just (->> branch (truncate trunk diff-only))))

(defn- resolve-to-trunk [trunk _ _ _ diff-only]
  (u/just (if diff-only () trunk)))

(defn merge-aols
  "Merge AOL ``branch`` into AOL ``trunk``.

  Parameter ``trunk`` (coll) is the AOL that will receive the changes.
  Parameter ``branch`` (coll) is the AOL that will be merged into trunk; the
    AOL that provides the changes.
  Keyword parameter ``:strategy`` (keyword) describes how to handle conflicts. If
    the strategy is ``:rebase``, we will append the new quads from ``branch``
    onto ``trunk``, despite the fact that this will result in integrated hashes
    for those appended quads that differ from their input hashes in ``branch``.
  Keyword parameter ``:diff-only`` (boolean) will, when true, result in a return
    value consisting simply of the suffix of the merged result that ``trunk``
    would need to append to itself in order to build the final merged result;
    when false, we return the entire merged AOL.
  Always returns a 2-tuple maybe-type structure."
  [trunk branch & {:keys [strategy diff-only]
                   :or {strategy :abort diff-only false}}]
  (let [new-from-branch (find-changes trunk branch)
        new-from-trunk (find-changes branch trunk)]
    ((cond (and (seq new-from-branch) (seq new-from-trunk)) resolve-conflict
           (seq new-from-branch) resolve-to-branch
           :else resolve-to-trunk)
     trunk branch new-from-branch strategy diff-only)))

(defn is-db-meta-key? [k] (= "db" (namespace k)))

(defn is-db-meta-entry? [[k _]] (is-db-meta-key? k))

(def is-not-db-meta-entry? (complement is-db-meta-entry?))

(defn entity->eavts
  "Given an entity map ``entity`` of type ``:dtaoldm.domain/type`` (a
  string), deterministically return a sequence of eavt vectors that would be
  sufficient to represent that domain entity in the append-only log."
  ([entity] (entity->eavts entity (u/get-now-str)))
  ([{e :db/id e-type :db/type e-extant :db/extant :as entity} t]
   (->> entity
        (filter is-not-db-meta-entry?)
        (sort-by key)
        (map (fn [[a v]] [e (name a) v t]))
        (concat [[e (if e-extant has-attr lacks-attr) being-val t]
                 [e is-a-attr e-type t]]))))

(defn aol-valid?
  "Compute whether ``aol`` is valid. Recompute all of its hashes and integrated
  hashes and ensure all are correct."
  [aol]
  (every? true?
          (mapcat
           (fn [[_ _ previous-integrated-hash] [quad hash integrated-hash]]
             (let [quad-hash (u/hash-data quad)]
               [(= hash quad-hash)
                (= integrated-hash
                   (-> [previous-integrated-hash quad-hash]
                       u/get-json
                       u/hash-string))]))
           (cons (take 3 (repeat nil)) aol)
           aol)))
