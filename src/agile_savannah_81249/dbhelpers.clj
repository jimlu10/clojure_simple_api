(ns agile-savannah-81249.dbhelpers
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.json]))

(defn db-get_project [proj-name]
  (let [connect-string "mongodb://127.0.0.1:27017/testdb"
        { :keys [conn db] } (mg/connect-via-uri connect-string)]
    (mc/find-maps db "project_catalog" { :name proj-name })))
