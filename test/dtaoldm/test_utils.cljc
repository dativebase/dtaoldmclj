(ns dtaoldm.test-utils "AOL Tests Utils"
    (:require [dtaoldm.aol :as aol]
              [dtaoldm.domain :as domain]
              [dtaoldm.utils :as u]
              #?(:clj [me.raynes.fs :as fs])))

(def resources-path (fs/file "resources"))

(def tmp-path (fs/file resources-path "tmp"))

(def test-old-instance-1
  (-> {:slug "oka" :name "Okanagan OLD" :url "http://127.0.0.1:5679/oka"}
      domain/construct-old-instance first))

(def test-old-instance-2
  (-> {:slug "bla" :name "Blackfoot OLD" :url "http://127.0.0.1:5679/bla"}
      domain/construct-old-instance first))

(defn generate-test-aol
  "Return an AOL list of Appendable (tuple) instances (encoding 2
  ``OLDInstance``s)."
  []
  (let [now-str (u/get-now-str)]
    (reduce aol/aol-append
            []
            (concat
             (aol/entity->eavts test-old-instance-1 now-str)
             (aol/entity->eavts test-old-instance-2 now-str)))))

(def test-aol (generate-test-aol))

(defn generate-large-test-aol
  "Return an AOL encoding ``n`` OLD instances."
  [n]
  (let [now-str (u/get-now-str)]
    (->> n
         range
         (mapcat (fn [_] (-> {:slug "oka" :name "Okanagan OLD"
                              :url "http://127.0.0.1:5679/oka"}
                             domain/construct-old-instance
                             first
                             (aol/entity->eavts now-str))))
         (reduce aol/aol-append []))))

(defn remove-test-files [& test-files]
  (doseq [fp test-files]
    (fs/delete fp)))

(defn clear-tmp-dir [] (apply remove-test-files (fs/list-dir tmp-path)))

(comment

  (remove-test-files "resources/tmp/a" "resources/tmp/b" "resources/tmp/dog")

  test-aol

  (aol/aol-valid? test-aol)

  (aol/aol->domain-entities test-aol)

  (aol/aol-valid? (generate-large-test-aol 200))

  (aol/aol->domain-entities (generate-large-test-aol 200))

)
