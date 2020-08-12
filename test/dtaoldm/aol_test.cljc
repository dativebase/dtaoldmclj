(ns dtaoldm.aol-test
  (:require [dtaoldm.aol :as sut]
            [dtaoldm.domain :as domain]
            [dtaoldm.utils :as u]
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])))

(def oka-old-id "ee07263b-ce9c-401f-9f71-4fa69ef3836b")

(def oka-old-instance
  (-> {:db/id oka-old-id
       :slug "oka"
       :name "oka"
       :url "http://127.0.0.1:5679/oka"}
      domain/construct-old-instance
      first))

(def oka-old-instance-updated
  (merge oka-old-instance
         {:dtaoldm.domain/is-auto-syncing true
          :dtaoldm.domain/leader "http://realworldoldservice.com/oka"
          :dtaoldm.domain/name "Okanagan OLD"}))

(defn generate-initial-aol []
  (reduce sut/aol-append [] (sut/entity->eavts oka-old-instance (u/get-now-str))))

(defn- apbl [h] [nil nil h])

(def find-changes-cases
  [;; 1. No change
   {:target [(apbl "a") (apbl "b") (apbl "c")]
    :mergee [(apbl "a") (apbl "b") (apbl "c")]
    :changes []}
   ;; 2. No conflict, new from target
   {:target [(apbl "a") (apbl "b") (apbl "c") (apbl "X")]
    :mergee [(apbl "a") (apbl "b") (apbl "c")]
    :changes []}
   ;; 3. No conflict, new from mergee
   {:target [(apbl "a") (apbl "b") (apbl "c")]
    :mergee [(apbl "a") (apbl "b") (apbl "c") (apbl "d")]
    :changes [(apbl "d")]}
   ;; 4. Conflict A, equal new
   {:target [(apbl "a") (apbl "b") (apbl "c") (apbl "X")]
    :mergee [(apbl "a") (apbl "b") (apbl "c") (apbl "d")]
    :changes [(apbl "d")]}
   ;; 5. Conflict B, more target new
   {:target [(apbl "a") (apbl "b") (apbl "c") (apbl "X") (apbl "Y")]
    :mergee [(apbl "a") (apbl "b") (apbl "c") (apbl "d")]
    :changes [(apbl "d")]}
   ;; 6. Conflict C, more mergee new
   {:target [(apbl "a") (apbl "b") (apbl "c") (apbl "X")]
    :mergee [(apbl "a") (apbl "b") (apbl "c") (apbl "d") (apbl "e")]
    :changes [(apbl "d") (apbl "e")]}
   ;; 1.i. Empty, no change
   {:target []
    :mergee []
    :changes []}
   ;; 1.ii. Short, no change
   {:target [(apbl "a")]
    :mergee [(apbl "a")]
    :changes []}
   ;; 2.i Short, no conflict, new from target
   {:target [(apbl "X")]
    :mergee []
    :changes []}
   ;; 3.i Short, no conflict, new from mergee
   {:target []
    :mergee [(apbl "d")]
    :changes [(apbl "d")]}
   ;; 4.i Short, conflict A, equal new
   {:target [(apbl "X")]
    :mergee [(apbl "d")]
    :changes [(apbl "d")]}
   ;; 5.i Short, conflict B, more target new
   {:target [(apbl "X") (apbl "Y")]
    :mergee [(apbl "d")]
    :changes [(apbl "d")]}
   ;; 6.i Short, conflict C, more mergee new
   {:target [(apbl "X")]
    :mergee [(apbl "d") (apbl "e")]
    :changes [(apbl "d") (apbl "e")]}])

(t/deftest find-changes-test
  (t/testing "aol/find-changes works as expected."
    (doseq [{:keys [target mergee changes]} find-changes-cases]
        (t/is (= (sut/find-changes target mergee) changes)))))

(defn- aolit
  "Create a fake tester AOL using all of the args in ``args``."
  [& args]
  (->> (for [arg args] (vec (repeat 4 arg)))
       (reduce sut/aol-append [])))

(def merge-aols-cases
  [;; Long AOLs
   ;; 1. No change
   {:target (aolit "a" "b" "c")
    :mergee (aolit "a" "b" "c")
    :merged (aolit "a" "b" "c")}
   ;; 2. No conflict, new from target
   {:target (aolit "a" "b" "c" "X")
    :mergee (aolit "a" "b" "c")
    :merged (aolit "a" "b" "c" "X")}
   ;; 3. No conflict, new from mergee
   {:target (aolit "a" "b" "c")
    :mergee (aolit "a" "b" "c" "d")
    :merged (aolit "a" "b" "c" "d")}
   ;; 4.a. Conflict A, equal new, ABORT
   {:target (aolit "a" "b" "c" "X")
    :mergee (aolit "a" "b" "c" "d")
    :err sut/need-rebase-err}
   ;; 4.b. Conflict A, equal new, MERGE
   {:target (aolit "a" "b" "c" "X")
    :mergee (aolit "a" "b" "c" "d")
    :strategy :rebase
    :merged (aolit "a" "b" "c" "X" "d")}
   ;; 5.a. Conflict B, more target new, ABORT
   {:target (aolit "a" "b" "c" "X" "Y")
    :mergee (aolit "a" "b" "c" "d")
    :err sut/need-rebase-err}
   ;; 5.b. Conflict B, more target new, MERGE
   {:target (aolit "a" "b" "c" "X" "Y")
    :mergee (aolit "a" "b" "c" "d")
    :strategy :rebase
    :merged (aolit "a" "b" "c" "X" "Y" "d")}
   ;; 6.a. Conflict C, more mergee new, ABORT
   {:target (aolit "a" "b" "c" "X")
    :mergee (aolit "a" "b" "c" "d" "e")
    :err sut/need-rebase-err}
   ;; 6.b. Conflict C, more mergee new, REBASE
   {:target (aolit "a" "b" "c" "X")
    :mergee (aolit "a" "b" "c" "d" "e")
    :strategy :rebase
    :merged (aolit "a" "b" "c" "X" "d" "e")}

   ;; Short AOLs

   ;; 1.i. Empty, no change
   {:target (aolit)
    :mergee (aolit)
    :merged (aolit)}
   ;; 1.ii. Short, no change
   {:target (aolit "a")
    :mergee (aolit "a")
    :merged (aolit "a")}
   ;; 2.i Short, no conflict, new from target
   {:target (aolit "X")
    :mergee (aolit)
    :merged (aolit "X")}
   ;; 3.i Short, no conflict, new from mergee
   {:target (aolit)
    :mergee (aolit "d")
    :merged (aolit "d")}
   ;; 4.i.a Short, conflict A, equal new, ABORT
   {:target (aolit "X")
    :mergee (aolit "d")
    :err sut/need-rebase-err}
   ;; 4.i.b Short, conflict A, equal new, REBASE
   {:target (aolit "X")
    :mergee (aolit "d")
    :strategy :rebase
    :merged (aolit "X" "d")}
   ;; 5.i.a Short, conflict B, more target new, ABORT
   {:target (aolit "X" "Y")
    :mergee (aolit "d")
    :err sut/need-rebase-err}
   ;; 5.i.b Short, conflict B, more target new, REBASE
   {:target (aolit "X" "Y")
    :mergee (aolit "d")
    :strategy :rebase
    :merged (aolit "X" "Y" "d")}
   ;; 6.i.a Short, conflict C, more mergee new, ABORT
   {:target (aolit "X")
    :mergee (aolit "d" "e")
    :err sut/need-rebase-err}
   ;; 6.i.b Short, conflict C, more mergee new, REBASE
   {:target (aolit "X")
    :mergee (aolit "d" "e")
    :strategy :rebase
    :merged (aolit "X" "d" "e")}

   ;; With :diff-only true

   ;; 1. No change
   {:target (aolit "a" "b" "c")
    :mergee (aolit "a" "b" "c")
    :diff-only true
    :merged (aolit)}
   ;; 2. No conflict, new from target
   {:target (aolit "a" "b" "c" "X")
    :mergee (aolit "a" "b" "c")
    :diff-only true
    :merged (aolit)}
   ;; 3. No conflict, new from mergee
   {:target (aolit "a" "b" "c")
    :mergee (aolit "a" "b" "c" "d")
    :diff-only true
    :merged (drop 3 (aolit "a" "b" "c" "d"))}
   ;; 4.a. Conflict A, equal new, ABORT
   {:target (aolit "a" "b" "c" "X")
    :mergee (aolit "a" "b" "c" "d")
    :diff-only true
    :err sut/need-rebase-err}
   ;; 4.b. Conflict A, equal new, MERGE
   {:target (aolit "a" "b" "c" "X")
    :mergee (aolit "a" "b" "c" "d")
    :diff-only true
    :strategy :rebase
    :merged (drop 4 (aolit "a" "b" "c" "X" "d"))}
   ;; 5.a. Conflict B, more target new, ABORT
   {:target (aolit "a" "b" "c" "X" "Y")
    :mergee (aolit "a" "b" "c" "d")
    :diff-only true
    :err sut/need-rebase-err}
   ;; 5.b. Conflict B, more target new, MERGE
   {:target (aolit "a" "b" "c" "X" "Y")
    :mergee (aolit "a" "b" "c" "d")
    :diff-only true
    :strategy :rebase
    :merged (drop 5 (aolit "a" "b" "c" "X" "Y" "d"))}
   ;; 6.a. Conflict C, more mergee new, ABORT
   {:target (aolit "a" "b" "c" "X")
    :mergee (aolit "a" "b" "c" "d" "e")
    :diff-only true
    :err sut/need-rebase-err}
   ;; 6.b. Conflict C, more mergee new, REBASE
   {:target (aolit "a" "b" "c" "X")
    :mergee (aolit "a" "b" "c" "d" "e")
    :diff-only true
    :strategy :rebase
    :merged (drop 4 (aolit "a" "b" "c" "X" "d" "e"))}])

(t/deftest merge-aols-test
  (t/testing "aol/merge-aols works as expected."
    (doseq [{:keys [target mergee merged
                    strategy diff-only err]
             :or {strategy :abort diff-only false err nil}}
            merge-aols-cases]
      (let [[actual-merged actual-err]
            (sut/merge-aols
             target
             mergee
             :strategy strategy
             :diff-only diff-only)]
        (if err
          (t/is (= err actual-err))
          (t/is (= merged actual-merged)))))))

(comment

  (generate-initial-aol)

  (let [new-from-target
        [[["X" "X" "X" "X"]
          "a8d3e115a1b419295df1b6860f0a3267"
          "9f917f50674577a6fb4b1d3f13f7f125"]]
        new-from-mergee
        [["d" "d" "d" "d"]]]
    (reduce sut/aol-append new-from-target new-from-mergee))


)
