(ns dtaoldm.domain
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [dtaoldm.utils :as u]))

(def gen-uuid-str
  (gen/elements (for [_ (range 1000)] (u/generate-uuid-str))))

(def gen-slug
  (gen/fmap
   (fn [x] (let [c (u/remove-non-slug-chars x)]
             (if (= "" c) "a" c)))
   gen/string-ascii))

(s/def ::uuid-str
  (s/with-gen
    u/is-uuid-string?
    (constantly gen-uuid-str)))

(s/def :db/id ::uuid-str)

(def old-instance-type "old-instance")
(def dative-app-type "dative-app")
(def old-service-type "old-service")

(s/def :db/type string?)

(s/def :db/extant boolean?)

;; Unique among OLD instances at a given OLDService.url e.g., "oka"
(s/def ::slug
  (s/with-gen
    u/slug?
    (constantly gen-slug)))

;; Human readable name, e.g., "Okanagan"
(s/def ::name string?)

;; URL of the OLD instance, typically the slug suffixed to the URL of a local OLD
;; web service
(s/def ::url string?)

;; URL, the URL of an external OLD instance that this OLD instance follows and
;; with which it syncs.
(s/def ::leader string?)

(s/def ::state #{"synced" "syncing" "not-in-sync"})

;; Indicates whether DativeTop should continuously and automatically keep this
;; local OLD instance in sync with its leader.
(s/def ::is-auto-syncing boolean?)

(s/def ::old-instance
  (s/keys :req [:db/id
                :db/type
                :db/extant
                ::slug
                ::name
                ::url
                ::leader
                ::state
                ::is-auto-syncing]))

(def old-instance-defaults
  {:db/id u/generate-uuid-str
   :db/type old-instance-type
   :db/extant true
   ::slug "slug"
   ::name ""
   ::url ""
   ::leader ""
   ::state "not-in-sync"
   ::is-auto-syncing false})

(defn get-default [defaults-map k]
  (let [d (k defaults-map)]
    (if (fn? d) (d) d)))

(defn get-spec-keys [spec] (->> spec s/describe (drop 2) first))

(defn get-domain-entity-kv [defaults m k]
  [k (get m k
          (get m (-> k name keyword)
               (get-default defaults k)))])

(defn construct
  ([spec defaults validator] (construct spec defaults validator {}))
  ([spec defaults validator m]
   (->> spec
        get-spec-keys
        (map (partial get-domain-entity-kv defaults m))
        (into {})
        validator)))

(defn validate
  [spec e-name entity]
  (if (s/valid? spec entity)
    (u/just entity)
    (u/nothing (u/format "Unable to construct a valid %s. %s."
                         e-name (s/explain-str spec entity)))))

(def validate-old-instance (partial validate ::old-instance "OLD instance"))

(def construct-old-instance
  (partial construct ::old-instance old-instance-defaults validate-old-instance))

(s/def ::dative-app
  (s/keys :req [:db/id
                :db/type
                :db/extant
                ::url]))

(def dative-app-defaults
  {:db/id u/generate-uuid-str
   :db/type dative-app-type
   :db/extant true
   ::url ""})

(def validate-dative-app (partial validate ::dative-app "Dative app"))

(def construct-dative-app
  (partial construct ::dative-app dative-app-defaults validate-dative-app))

(s/def ::old-service
  (s/keys :req [:db/id
                :db/type
                :db/extant
                ::url]))

(def old-service-defaults
  {:db/id u/generate-uuid-str
   :db/type old-service-type
   :db/extant true
   ::url ""})

(def validate-old-service (partial validate ::old-service "OLD service"))

(def construct-old-service
  (partial construct ::old-service old-service-defaults validate-old-service))

(comment

  (construct-old-instance {:slug "2"})

  (construct-old-instance {:slug 2})

  (construct-old-instance {})

  (construct-old-instance)

  (construct-dative-app {:url "2"})

  (construct-dative-app {:url 2})

  (construct-dative-app)

  (construct-old-service {:url "2"})

  (construct-old-service {:url 2})

  (construct-old-service)

  (gen/generate (s/gen :db/id))

  (gen/generate gen-slug)

  (gen/generate (s/gen ::old-instance))

  (gen/generate (s/gen ::dative-app))

)
