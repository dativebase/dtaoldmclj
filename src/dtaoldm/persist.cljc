(ns dtaoldm.persist
  #?(:cljs (:refer-clojure :exclude [hash-string]))
  (:require [clojure.string :as str]
            [dtaoldm.utils :as u]
            #?(:clj [me.raynes.fs :as fs])
            #?(:cljs [goog.crypt])
            #?(:cljs [goog.crypt.Md5]))
  #?(:clj (:import
           (java.util Calendar)
           (java.util TimeZone)
           (java.text SimpleDateFormat)
           (java.security MessageDigest)
           (java.math BigInteger))))

(defn serialize-appendable [appendable]
  (str (u/get-json appendable) \newline))

(defn aol->str [aol]
  (->> aol
       (map serialize-appendable)
       str/join))

#?(:clj
   (defn read-aol
     [file-path]
     (->> file-path
          slurp
          str/split-lines
          (map (comp vec u/parse-json))
          vec)))

#?(:clj
   (defn get-tip-hash-in-file
     "Get the integrated hash of the last line (= EAVT quad) in the append-only log
  at path ``file-path``. Note: this may be inefficient on large files."
     [file-path]
     (when (fs/file? file-path)
       (with-open [rdr (clojure.java.io/reader file-path)]
         (-> rdr
             line-seq
             last
             u/parse-json
             last)))))

(defn get-new-appendables
  "Return all appendables in ``aol`` that come after the appendable with
  integrated hash ``tip-hash``."
  [aol tip-hash]
  (if-not tip-hash
    aol
    (->> aol
         (drop-while (fn [[_ _ i-hash]] (not= i-hash tip-hash)))
         (drop 1))))

#?(:clj
   (defn append-aol-to-file
     "Write all of the new appendables in the append-only log ``aol`` to the file
     at path ``file_path``."
     [aol file-path]
     (let [tip-hash (get-tip-hash-in-file file-path)]
       (when-not (fs/file? file-path) (fs/touch file-path))
       (spit file-path
             (-> aol
                 (get-new-appendables tip-hash)
                 aol->str)
             :append true))))

#?(:clj (def persist-aol append-aol-to-file))

#?(:clj
   (defn write-aol-to-file
     [aol file-path]
     (->> aol
          aol->str
          (spit file-path))))
