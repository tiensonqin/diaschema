(ns diaschema.core
  (:gen-class)
  (:require [clojure.java.jdbc :as j]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [clojure.string :as string])
  (:import  [javax.imageio ImageIO]
            [java.awt.image RenderedImage]))

(defn db
  [name]
  {:dbtype (or (:db-type env) "postgresql")
   :dbname name
   :host (or (:db-host env) "127.0.0.1")
   :user (:db-user env)
   :password (or (:db-passwd env) "")})

;; TODO: replace with (with-db-metadata)
(defn get-tables [conn]
  (->> (.getTables (.getMetaData (:connection conn)) nil nil "%" (into-array String ["TABLE"]))
       resultset-seq
       (map (fn [{:keys [table_schem table_name remarks]}]
              {:schema table_schem
               :name table_name
               :description remarks}))))

(defn get-columns
  ([conn table]
   (get-columns conn "public" table))
  ([conn schema table]
   (->> (.getColumns (.getMetaData (:connection conn)) nil schema table nil)
        resultset-seq
        (map (fn [{:keys [column_name type_name column_size is_nullable is_autoincrement]}]
               {:size column_size
                :name column_name
                :type type_name
                :nilable? (= "YES" is_nullable)
                :auto-increment? (= "YES" is_autoincrement)})))))

(defn get-schema
  [db]
  (some->>
   (j/with-db-connection [conn db]
     (let [tables (get-tables conn)]
       (when (seq tables)
         (doall
          (map (fn [table]
                 (assoc table
                        :columns (get-columns conn (:name table))))
            tables)))))))

(defn table->dot
  [{:keys [schema name description columns]}]
  (let [columns (map (fn [{:keys [name type nilable?]}]
                       (format "  <tr><td>%s :- %s (null? %s)</td></tr>" name type nilable?))
                  columns)
        columns (->> (interpose "\n" columns)
                     (apply str))
        tbl (str "<table border=\"0\" cellborder=\"1\" cellspacing=\"0\">\n"
                 (format "  <tr><td><font point-size='24'><b><i>Table %s</i></b></font></td></tr>\n" name)
                 columns
                 "\n</table>")]
    (format "%s [label=<\n%s>];\n" name tbl)))

(defn schema->dot
  [db-name schema]
  (let [begin (format "digraph {
    graph [pad=\"0.5\", nodesep=\"0.5\", ranksep=\"2\"];
    node [shape=plain]
    rankdir=LR;
    labelloc=t;
    label=< <font point-size='48'><b><i>DB %s</i></b></font> >\n
" db-name)
        tables (->> (map table->dot schema)
                    (interpose "\n\n")
                    (apply str))
        ;; rels {}
        rels ""
        ]
    (str begin
         tables
         "\n"
         rels
         "\n"
         "}")))

;; borrowed from https://github.com/ztellman/rhizome/blob/master/src/rhizome/viz.clj#L108

(defn- format-error [s err]
  (apply str
    err "\n"
    (interleave
     (map
       (fn [idx s]
         (format "%3d: %s" idx s))
       (range)
       (str/split-lines s))
     (repeat "\n"))))

(defn dot->image
  "Takes a string containing a GraphViz dot file, and renders it to an image.  This requires that GraphViz
   is installed on the local machine."
  [s]
  (let [{:keys [out err]} (try
                            (sh/sh "dot" "-Tpng" :in s :out-enc :bytes)
                            (catch java.io.IOException e
                              (try
                                (sh/sh "dot" "-v")
                                (throw e) ;; dot is working fine, something else is broken
                                (catch java.io.IOException e
                                  (throw (RuntimeException.
                                          "Couldn't find `dot` executable: have you installed graphviz?"
                                          e))))))]
    (or
     (ImageIO/read (io/input-stream out))
     (throw (IllegalArgumentException. ^String (format-error s err))))))

(defn save-image
  "Saves the given image buffer to the given filename. The default
file type for the image is png, but an optional type may be supplied
as a third argument."
  ([image filename]
   (save-image image "png" filename))
  ([^RenderedImage image ^String filetype filename]
   (ImageIO/write image filetype (io/file filename))))

(defn save-graph
  "Takes a graph descriptor in the style of `graph->dot`, and saves the image to disk."
  [dot filename]
  (-> dot
      dot->image
      (save-image filename)))

(defn generate
  [db-names output-dir-path]
  (doseq [db-name db-names]
    (prn {:spec (db db-name)})
    (let [schema (get-schema (db db-name))
          dot (schema->dot db-name schema)]
      (save-graph dot (str output-dir-path "/" db-name ".png")))))

;; Env
;; 0. DB_TYPE
;; 1. DB_USER
;; 2. DB_PASSWD
;; 3. DB_HOST
;; 4. DB_NAMES
(defn -main
  [& _]
  (let [db-names (let [names (get env :db-names)]
                   (if names (string/split names #",")))
        output-dir-path (get env :output-dir (System/getProperty "user.dir"))]
    (if (seq db-names)
      (generate db-names output-dir-path)
      (println "Please specify at least one db name!"))))
