(ns clojars.diff
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh])
  (:import (java.util.zip ZipFile ZipEntry)
           (org.apache.commons.codec.digest DigestUtils)))

(defn entry-checksums [jar]
  (with-open [z (ZipFile. jar)]
    (into {} (for [e (enumeration-seq (.entries z))]
               [(.getName e) (DigestUtils/shaHex (.getInputStream z e))]))))

(defn diff [k j1 j2]
  (let [t1 (java.io.File/createTempFile "clojars" "diff")
        t2 (java.io.File/createTempFile "clojars" "diff")]
    (with-open [z1 (ZipFile. j1)
                z2 (ZipFile. j2)]
      (if (and (.getEntry z1 k) (.getEntry z2 k))
        (do
          (io/copy (.getInputStream z1 (.getEntry z1 k)) t1)
          (io/copy (.getInputStream z2 (.getEntry z2 k)) t2))
        (println "Only in one jar:" k (.getEntry z1 k) (.getEntry z2 k)))
      (println (:out (sh/sh "diff" "-u" (str t1) (str t2)))))
    (.delete t1)
    (.delete t2)))

;; Given two jar files, find all the contents that don't match and
;; print a diff for them.
(defn diff-jar [j1 j2]
  (let [c1 (entry-checksums j1)
        c2 (entry-checksums j2)]
    (doseq [k (set (concat (keys c1) (keys c2)))
            :when (not= (c1 k) (c2 k))]
      (println "Mismatch:" k)
      (diff k j2 j1))))

(defn get-from-clojars [j1 dir]
  (let [j2 (doto (java.io.File/createTempFile "clojars" "diff")
             (.deleteOnExit))
        rel (subs (str j1) (count dir))]
    (io/copy (.openStream (java.net.URL. (str "https://clojars.org/repo" rel)))
             j2)
    j2))

(defn -main [dir]
  (doseq [j (file-seq (io/file dir))
          :when (.endsWith (.getName j) ".jar")]
    (try
      (println "Diffing" (str j))
      (diff-jar j (get-from-clojars j dir))
      (catch Exception e
        (println j (.getMessage e))))))

;; results at http://p.hagelb.org/clojars-republished.diff.html