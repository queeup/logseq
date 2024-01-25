(ns logseq.db.sqlite.create-graph
  "Helper fns for creating a DB graph"
  (:require [logseq.db.sqlite.util :as sqlite-util]
            [logseq.db.frontend.schema :as db-schema]
            [logseq.db.frontend.property :as db-property]
            [logseq.db.frontend.class :as db-class]
            [logseq.db.frontend.property.util :as db-property-util]
            [logseq.common.util :as common-util]
            [datascript.core :as d]
            [logseq.db :as ldb]))

(defn- build-initial-properties
  []
  (let [;; Some uuids need to be pre-defined since they are referenced by other properties
        default-property-uuids {:icon (d/squuid)}]
    (mapcat
     (fn [[k-keyword {:keys [schema original-name closed-values]}]]
       (let [k-name (name k-keyword)]
         (if closed-values
           (db-property-util/build-closed-values
            (or original-name k-name)
            {:block/schema schema :block/uuid (d/squuid) :closed-values closed-values}
            {:icon-id (get default-property-uuids :icon)})
           [(sqlite-util/build-new-property
             {:block/schema schema
              :block/original-name (or original-name k-name)
              :block/name (common-util/page-name-sanity-lc k-name)
              :block/uuid (get default-property-uuids k-keyword (d/squuid))})])))
     db-property/built-in-properties)))

(defn build-db-initial-data
  [config-content]
  (let [initial-data [{:db/ident :db/type :db/type "db"}
                      {:db/ident :schema/version :schema/version db-schema/version}]
        initial-files [{:block/uuid (d/squuid)
                        :file/path (str "logseq/" "config.edn")
                        :file/content config-content
                        :file/last-modified-at (js/Date.)}
                       {:block/uuid (d/squuid)
                        :file/path (str "logseq/" "custom.css")
                        :file/content ""
                        :file/last-modified-at (js/Date.)}
                       {:block/uuid (d/squuid)
                        :file/path (str "logseq/" "custom.js")
                        :file/content ""
                        :file/last-modified-at (js/Date.)}]
        default-pages (ldb/build-default-pages-tx)
        default-properties (build-initial-properties)
        name->properties (zipmap
                          (map :block/name default-properties)
                          default-properties)
        default-classes (map
                         (fn [[k-keyword {:keys [schema original-name]}]]
                           (let [k-name (name k-keyword)]
                             (sqlite-util/build-new-class
                              {:block/original-name (or original-name k-name)
                               :block/name (common-util/page-name-sanity-lc k-name)
                               :block/uuid (d/squuid)
                               :block/schema {:properties
                                              (mapv
                                               (fn [property-name]
                                                 (let [id (:block/uuid (get name->properties property-name))]
                                                   (assert id (str "Built-in property " property-name " is not defined yet"))
                                                   id))
                                               (:properties schema))}})))
                         db-class/built-in-classes)]
    (vec (concat initial-data initial-files default-pages default-classes default-properties))))
