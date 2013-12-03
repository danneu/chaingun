(ns chaingun.cli
  (:require
   [chaingun.util :refer :all]
   [chaingun.db :as db]
   ;[chaingun.codec :as codec]
   ;[chaingun.codec2 :as codec2]
   [chaingun.seed :as seed]
   [chaingun.explorer :as explorer])
  (:gen-class))

;; Command handler ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti execute first)

(defmethod execute "db:delete" [_]
  (println "Deleting database...")
  (db/delete-database)
  (println "Done.")
  (System/exit 0))

(defmethod execute "db:create" [_]
  (println "Creating database and schema...")
  (db/create-database)
  (println "Done.")
  (System/exit 0))

;; (defmethod execute "bench1" [_]
;;   (seed/seed)
;;   (System/exit 0))

(defmethod execute "db:import-dat2" [_]
  ;(codec2/import-dat)
  (seed/import-dat)
  (System/exit 0))
;; (defmethod execute "db:seed00000" [_]
;;   (codec/seed-db)
;;   (System/exit 0))

;; (defmethod execute "demo:seed" [_]
;;   (codec/seed-db-demo)
;;   (System/exit 0))

(defmethod execute "explorer" [[_ & [port & _]]]
  (let [port (Integer. (or port "3000"))]
    (println (str "Hosting database explorer at "
                  "http://localhost:" port "/"))
    (explorer/start-server port)))

(defmethod execute "echo" [_] (println _))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main [& args]
  (println "Chaingun launched.\n")
  (execute args))
