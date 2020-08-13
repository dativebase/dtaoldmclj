================================================================================
  DativeTop Append-Only Log Domain Model (dtaoldm)
================================================================================

The DativeTop Append-Only Log Domain Model in Clojure (dtaoldmclj) defines the
DativeTop domain entities (OLDInstance, DativeApp, OLDService) and functions for
converting those entities to EAVT quadruples that can be stored in a
Clojure-native append-only log.


Run the Tests
================================================================================

Run::

    $ clj -A:test
    Ran 6 tests containing 45 assertions.
    0 failures, 0 errors.


Usage
================================================================================

Start a Clojure REPL and require the core namespaces::

    $ clj
    Clojure 1.10.1
    user=> (require '[dtaoldm.aol :as aol])
    user=> (require '[dtaoldm.domain :as dom])
    user=> (require '[dtaoldm.utils :as u])

Define four DativeTop domain entities: an OLD instance::

    user=> (def old-instance-1 (-> {:slug "oka" :name "Okanagan OLD"
                                    :url "http://127.0.0.1:5679/oka"}
                                   dom/construct-old-instance first))
    ;; {:db/extant true
    ;;  :db/type "old-instance"
    ;;  :dtaoldm.domain/state "not-in-sync"
    ;;  :dtaoldm.domain/slug "oka"
    ;;  :dtaoldm.domain/is-auto-syncing false
    ;;  :dtaoldm.domain/leader ""
    ;;  :dtaoldm.domain/url "http://127.0.0.1:5679/oka"
    ;;  :db/id "b24c9b24-ac48-4bee-8484-58c47ec0bc26"
    ;;  :dtaoldm.domain/name "Okanagan OLD"}

another OLD instance::

    user=> (def old-instance-2 (-> {:slug "bla" :name "Blackfoo OLD"
                                    :url "http://127.0.0.1:5679/bla"}
                                   dom/construct-old-instance first))
    ;; {:db/extant true
    ;;  :db/type "old-instance"
    ;;  :dtaoldm.domain/state "not-in-sync"
    ;;  :dtaoldm.domain/slug "bla"
    ;;  :dtaoldm.domain/is-auto-syncing false
    ;;  :dtaoldm.domain/leader ""
    ;;  :dtaoldm.domain/url "http://127.0.0.1:5679/bla"
    ;;  :db/id "d187f9dd-5202-486d-ac2b-6acbae932911"
    ;;  :dtaoldm.domain/name "Blackfoo OLD"}

a Dative app::

    user=> (def dative-app (-> {:url "http://127.0.0.1:5678/"}
                               dom/construct-dative-app first))
    ;; {:db/id "76291050-68b1-4e55-8136-b414ec7570c3"
    ;;  :db/type "dative-app"
    ;;  :db/extant true
    ;;  :dtaoldm.domain/url "http://127.0.0.1:5678/"}

and an OLD service::

    user=> (def old-service (-> {:url "http://127.0.0.1:5679/"}
                                dom/construct-old-service first))
    ;; {:db/id "ddab297f-7a12-4338-a312-0de30fd54b48"
    ;;  :db/type "old-service"
    ;;  :db/extant true
    ;;  :dtaoldm.domain/url "http://127.0.0.1:5679/"}

Now we can convert these domain entities to EAVTs (event, attribute, value,
transaction 4-ary vectors) that can be appended to an AOL::

    user=> (def now-str (u/get-now-str))
    user=> (def old-instance-1-eavts (aol/entity->eavts old-instance-1 now-str))
    (["aa94c401-3719-4d55-9290-1895afa7a027" "db__extant" true
      "2020-08-13T18:15:18.030000"]
     ["aa94c401-3719-4d55-9290-1895afa7a027" "db__type" "old-instance"
      "2020-08-13T18:15:18.030000"]
     ["aa94c401-3719-4d55-9290-1895afa7a027" "dtaoldm.domain__is-auto-syncing" false
      "2020-08-13T18:15:18.030000"]
     ["aa94c401-3719-4d55-9290-1895afa7a027" "dtaoldm.domain__leader" ""
      "2020-08-13T18:15:18.030000"]
     ["aa94c401-3719-4d55-9290-1895afa7a027" "dtaoldm.domain__name" "Okanagan OLD"
      "2020-08-13T18:15:18.030000"]
     ["aa94c401-3719-4d55-9290-1895afa7a027" "dtaoldm.domain__slug" "oka"
      "2020-08-13T18:15:18.030000"]
     ["aa94c401-3719-4d55-9290-1895afa7a027" "dtaoldm.domain__state" "not-in-sync"
      "2020-08-13T18:15:18.030000"]
     ["aa94c401-3719-4d55-9290-1895afa7a027" "dtaoldm.domain__url" "http://127.0.0.1:5679/oka"
      "2020-08-13T18:15:18.030000"])
    user=> (def old-instance-2-eavts (aol/entity->eavts old-instance-2 now-str))
    user=> (def dative-app-eavts (aol/entity->eavts dative-app now-str))
    user=> (def old-service-eavts (aol/entity->eavts old-service now-str))

The sequences of EAVTs can be concatenated and used to construct an AOL as follows::

    user=> (def aol (reduce aol/aol-append
                      [] (concat old-instance-1-eavts
                                 old-instance-2-eavts
                                 dative-app-eavts
                                 old-service-eavts)))
    ;; [[["aa94c401-3719-4d55-9290-1895afa7a027" "db__extant" true
    ;;    "2020-08-13T18:15:18.030000"]
    ;;   "2add723ab1b488718ee41208ea79760e"
    ;;   "d3d433d03262a344e0d876581594fe90"]
    ;; [["aa94c401-3719-4d55-9290-1895afa7a027" "db__type" "old-instance"
    ;;   "2020-08-13T18:15:18.030000"]
    ;;  "02f7075929d2a8258b64a6982eca2222"
    ;;  "dec5664947fa01ea8fe90da080ab2b20"]
    ;; ...]

An AOL is a sequence of appendables. Each appendable is a 3-ary vector consisting
of the EAVT, the hash of the EAVT, and the integrated hash of the EAVT, i.e., the
hash of a 2-tuple consisting of the previous appendable's integrated hash and the
hash of the EAVT.

Validating an AOL means recomputing and verifying all of its hashes::

    user=> (aol/aol-valid? aol)  ;; true
