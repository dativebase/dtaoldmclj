(ns dtaoldm.utils
  #?(:clj (:refer-clojure :rename {format core-format}))
  #?(:cljs (:refer-clojure :exclude [hash-string]))
  (:require [clojure.string :as str]
            #?(:clj [cheshire.core :as ch])
            #?(:cljs [goog.crypt])
            #?(:cljs [goog.crypt.Md5])
            #?(:cljs [goog.string :as gstring])
            #?(:cljs [goog.string.format]))
  #?(:clj (:import
           (java.util Calendar)
           (java.util TimeZone)
           (java.text SimpleDateFormat)
           (java.security MessageDigest)
           (java.math BigInteger))))

(defn bind
  "Call f on val if err is nil, otherwise return [nil err]
  See https://adambard.com/blog/acceptable-error-handling-in-clojure/."
  [f [val err]]
  (if (nil? err)
    (f val)
    [nil err]))

(defmacro err->>
  "Thread-last val through all fns, each wrapped in bind.
  See https://adambard.com/blog/acceptable-error-handling-in-clojure/."
  [val & fns]
  (let [fns (for [f fns] `(bind ~f))]
    `(->> [~val nil]
          ~@fns)))

(defn just
  [x]
  [x nil])

(defn nothing
  [error]
  [nil error])

(defn generate-uuid-str
  []
  #?(:cljs
   (let [hex (fn [] (.toString (rand-int 16) 16))
         rhex (.toString (bit-or 0x8 (bit-and 0x3 (rand-int 16))) 16)]
     (uuid
      (str (hex) (hex) (hex) (hex)
           (hex) (hex) (hex) (hex) "-"
           (hex) (hex) (hex) (hex) "-"
           "4"   (hex) (hex) (hex) "-"
           rhex  (hex) (hex) (hex) "-"
           (hex) (hex) (hex) (hex)
           (hex) (hex) (hex) (hex)
           (hex) (hex) (hex) (hex))))
   :clj (str (java.util.UUID/randomUUID))))

(defn is-uuid-string?
  [x]
  (and (string? x)
       (= [8 4 4 4 12]
          (->> (str/split x #"-")
               (map (fn [p] (->> (str/lower-case p)
                                 (filter #(some #{%} (seq "abcdef0123456789")))
                                 count)))))))

(defn matches-slug-regex
  [x]
  (re-matches #"^[a-zA-Z0-9_-]+$" x))

(defn remove-non-slug-chars
  [x]
  (str/replace x #"[^a-zA-Z0-9_-]" ""))

(defn slug?
  "Returns true if `x` contains only letters, numbers, underscores and hyphens."
  [x]
  (and (string? x)
       (matches-slug-regex x)))

(defn format
  [fmt & args]
  #?(:clj (apply core-format fmt args))
  #?(:cljs (apply gstring/format fmt args)))

(defn get-json [d]
  #?(:clj (ch/generate-string d)
     :cljs (.stringify js/JSON (clj->js d))))

(defn parse-json [s]
  #?(:clj (ch/parse-string s)
     :cljs (.parse js/JSON s)))

(defn get-now []
  #?(:clj (.getTime (Calendar/getInstance))
     :cljs (js/Date.)))

(defn get-now-str
  "Return an ISO 8601 string representation of the current datetime. Use no
  dependencies. WARNING: microsecond precision is not present although it appears
  to be so since 3 zeroes are appended to the milliseconds. The purpose of this
  is to match the format of the representation returned by Python's
  ``datetime.datetime.utcnow().isoformat()``. This is important because the
  client-side ClojureScript AOL will be coordinating data with a server-side
  Python web service."
  []
  #?(:clj (let [timezone (TimeZone/getTimeZone "UTC")
                formatter (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS")]
            (.setTimeZone formatter timezone)
            (->> (.format formatter (get-now))
                 (#(concat % (take 3 (repeat \0))))
                 (apply str)))
     :cljs (->> (get-now)
                .toISOString
                (drop-last 1)
                (#(concat % (take 3 (repeat \0))))
                (apply str))))

(defn hash-string
  "Taken from https://github.com/thedavidmeister/cljc-md5/blob/master/src/md5/core.cljc"
  [s]
  {:pre [(string? s)]
   :post [(string? %)]}
  #?(:cljs
     (goog.crypt/byteArrayToHex
      (let [md5 (goog.crypt.Md5.)]
        (.update md5 (goog.crypt/stringToUtf8ByteArray s))
        (.digest md5)))
     :clj
     (let [algorithm (MessageDigest/getInstance "MD5")
           raw (.digest algorithm (.getBytes s))]
       (format "%032x" (BigInteger. 1 raw)))))

(defn hash-data [d] (-> d get-json hash-string))
