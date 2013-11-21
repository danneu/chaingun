(ns hyzhenhok.cli
  (:require
   [hyzhenhok.util :refer :all]
   [hyzhenhok.db :as db]
   [hyzhenhok.codec :as codec]
   [hyzhenhok.explorer :as explorer])
  (:gen-class))

;; Command handler ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti execute first)

(defmethod execute "demo:seed" [_]
  (codec/seed-db)
  (System/exit 0))

(defmethod execute "explorer" [[_ & [port & _]]]
  (let [port (Integer. (or port "3000"))]
    (println (str "Hosting database explorer at "
                  "http://localhost:" port "/"))
    (explorer/start-server port)))

(defmethod execute "echo" [_] (println _))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main [& args]
  (println "Hyzhenhok launched.\n")
  (execute args))
