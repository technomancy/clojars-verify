(ns clojars.verify
  (:require [clucy.core :as c]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.java.shell :as sh])
  (:import (java.util.zip ZipFile ZipEntry)))

(def jar-pattern #"/([^/]+)/([^/]+)/([^/]+)/([^/]+)\.jar$")

(defn query-for [coords]
  (apply format "g:%s AND a:%s AND v:%s" coords))

(defn sha [x]
  (if (instance? java.io.File x)
    (first (.split (:out (sh/sh "sha1sum" (str x))) " "))
    (let [tmp (doto (java.io.File/createTempFile "clojars" "verify")
                (.deleteOnExit))]
      (io/copy x tmp)
      (recur tmp))))

(defn jar-mismatch? [doc f]
  (let [from-index (:1 doc)
        from-repo (sha (io/input-stream f))]
    (if (not= from-index from-repo)
      (format "jar mismatch, index: %s repo: %s" from-index from-repo))))

(defn pom-mismatch? [coords f]
  (let [pom-entry-name (apply format "META-INF/maven/%s/%s/pom.xml" coords)
        pom-file-name (s/replace (str f) #"\.jar" ".pom")
        z (ZipFile. f)
        entry (.getEntry z pom-entry-name)]
    (if entry
      (let [from-jar (sha (.getInputStream z entry))
            from-repo (sha (io/input-stream f))]
        (if (not= from-jar from-repo)
          (format "pom mismatch, jar: %s repo %s" from-jar from-repo)))
      "no pom in jar")))

(defn -main [index-location repository & [limit]]
  (with-open [index (c/disk-index index-location)]
    (doseq [f (if limit
                (take (Integer. limit) (file-seq (io/file repository)))
                (file-seq (io/file repository)))
            :when (.endsWith (.getName f) ".jar")
            :let [coords (rest (re-find jar-pattern (str f)))
                  doc (first (c/search index (query-for coords) 1))]]
      (println (subs (str f) (inc (count repository))) "-"
               (or (if (nil? doc) "not in index")
                   (jar-mismatch? doc f)
                   (pom-mismatch? coords f)
                   "verified.")))))
