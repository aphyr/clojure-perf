(ns clojure-perf.core
  (:import [java.io PushbackReader])
  (:require [schadenfreude.core :as s]
            [incanter.core :as incanter]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]))

(def suite
  {:runs [{:name    "single keyword created and released"
           :f       (fn [_] (keyword "rgfhs89egj9j4"))
           :threads 4
           :n       1e3}
          {:name    "thousand keywords created and released"
           :before  (fn [_] (mapv (partial str "test-kw-") (range 1000)))
           :f       #(mapv keyword %)
           :threads 4
           :n       1e3}
          {:name    "100K keywords created and released"
           :before  (fn [_] (mapv (partial str "test-kw-") (range 100000)))
           :f       #(mapv keyword %)
           :threads 4
           :n       1e1}
;          ]})
;(def suite
;  {:runs [
          {:name    "JSON parsing"
           :before  (fn [_] (slurp "data/test.json"))
           :f       #(json/parse-string % true)
           :threads 4
           :n       1e3}
          ]})

(defn serialize!
  "Write a term as edn to disk"
  [filename x]
  (with-open [f (io/writer filename)]
    (binding [*print-dup* true]
      (pprint x f))))

(defn deserialize
  "Read an EDN term from disk"
  [filename]
  (with-open [f (PushbackReader. (io/reader filename))]
    (edn/read f)))

(defn suite-path
  [path]
  (str "suites/" path))

(defn ->suite
  "Reconstitute a suite from an EDN structure"
  [suite]
  (->> (:runs suite)
       (map (fn [run]
              (if-let [r (:record run)]
                (assoc run :record (incanter/dataset
                                     (:column-names r)
                                     ; Drop final row; we don't want artifacts
                                     ; from termination time here.
                                     (butlast (:rows r))))
                run)))
       (assoc suite :runs)))

(defn suites
  "All suites on disk"
  []
  (->> "suites"
       io/file
       .listFiles
       (map deserialize)
       (map ->suite)))

(defn rebuild-graphs!
  "Rebuild graphs from all suites"
  []
  (->> (suites)
       (mapcat (fn [suite]
                 (map (fn [run]
                        (assoc run
                               :name  (:name suite)
                               :graph (:name run)))
                      (:runs suite))))
       (group-by :graph)
       (map (fn [[graph-name runs]]
              (incanter/save (s/latency-plot runs)
                             (str "graphs/" graph-name " latency 0.5.png")
                             :width 1024)
              (incanter/save (s/latency-plot 0.999 runs)
                             (str "graphs/" graph-name " latency 0.999.png")
                             :width 1024)
              (incanter/save (s/throughput-plot runs)
                             (str "graphs/" graph-name " throughput.png")
                             :width 1024)))
       dorun))

(defn -main [version & args]
  (read-line)
  (->> suite
       s/record-suite
       :runs
       (map #(select-keys % [:record :name :threads :n]))
       (array-map :name version :runs)
       (serialize! (suite-path version)))
  (read-line)
;  (rebuild-graphs!)
  (println)
  (System/exit 0))
