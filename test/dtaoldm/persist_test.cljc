(ns dtaoldm.persist-test
  (:require #?(:clj [dtaoldm.persist :as sut])
            [dtaoldm.test-utils :as u]
            #?(:clj [me.raynes.fs :as fs])
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])))

(defn clean-tmp-dir-fixture [f]
  (u/clear-tmp-dir)
  (f)
  (u/clear-tmp-dir))

(t/use-fixtures :each clean-tmp-dir-fixture)

;; ============================================================================
;; Filesystem I/O Tests
;; ============================================================================

#?(:clj
   (t/deftest test-aol-persistence
     (t/testing
         "Test that ``sut/persist-aol`` works correctly when the entire AOL is
          written to disk in one go, and when it is performed in stages, i.e.,
          with an initial write and a subsequent append."
       (let [test-aol (u/generate-test-aol)
             path-write (fs/file u/tmp-path "aol-write.txt")
             path-write-append (fs/file u/tmp-path "aol-write-append.txt")]
         (sut/persist-aol test-aol path-write)
         (sut/persist-aol (take 5 test-aol) path-write-append)
         (sut/persist-aol test-aol path-write-append)
         (t/is (= (slurp path-write) (slurp path-write-append)))))))

#?(:clj
   (t/deftest test-aol-persistence-existing-empty-file
     "Test that ``sut/persist-aol`` works when there is already an empty file at the
  destination path."
     (let [test-aol (u/generate-test-aol)
           path (fs/file u/tmp-path "aol-initially-empty.txt")
           path-new (fs/file u/tmp-path "aol.txt")]
       (fs/touch path)
       (sut/persist-aol test-aol path)
       (sut/persist-aol test-aol path-new)
       (t/is (= (slurp path) (slurp path-new))))))

(comment

  (let [test-aol (u/generate-test-aol)
        path (fs/file u/tmp-path "aol-initially-empty.txt")]
    (sut/persist-aol test-aol path)
    (let [read-aol (sut/read-aol path)]
      read-aol))

)
