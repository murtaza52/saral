(ns saral.core
  (:require [saral.ssh :refer [get-agent run-on-server]]
            [clojure.string :refer [join split trim]]
            [environ.core :refer [env]]
            [clojure.set :refer [difference intersection]]
            [slingshot.slingshot :refer [throw+ try+]]
            [clostache.parser :as clo]))

(defn get-cmd [args fns]
  (->> (map
        (fn[f]
          (if (string? f)
            (clo/render f args)
            (f args)))
        fns)
       (clojure.string/join "; " )))

(comment (get-cmd {:packages {:nginx {:version 1.7}}} ["ls" "yum install nginx {{packages.nginx.version}}" "echo hello"])
         (get-cmd {:say {:hello "from args"}} ["cd /" "ls" "echo hello {{say.hello}}"]))

(defn contains-tags [f s]
  (= (set s) (intersection (set f) (set s))))

(comment (contains-tags [:a :b] [:a :b :c]))

(defn filter-servers [servers tags]
  (filter #(contains-tags (:tags %) tags)
          servers))

(comment (filter-servers [{:tags [:uat :app-server]}
                          {:tags [:prod :app-server]}]
                         [:uat :app-server])
         (filter-servers (env :servers)
                         [:uat :app-server]))

(def tags-config {:uat {:fns [["echo uat!!"]]}
                  :prod {:fns [["echo prod!!"]]}
                  :app-server {:fns [["cd /" "ls"]
                                     ["echo hello {{say.hello}}"]]
                               :config {:say {:hello "from run time config"}}}})

(defn filter-tags [tags-coll tags]
  (select-keys tags-coll tags))

(comment (filter-tags tags-config [:uat :app-server]))

(defn print-cmd-run [cmds {:keys [exit out err]}]
  (doseq [cmd (split cmds #"; ")]
    (println (str "=> " cmd)))
  (println "---------------------------------------------------------------------------------------------------------------")
  (if (= 0 exit)
    (do
      (println (trim out))
      (newline))
    (do
      (println "ERROR")
      (println err))))

(comment (print-cmd-run "ls; echo hello" {:exit 0, :out "faiz-static\nfaiz.service\nhello\n", :err ""}))

(defn print-cmd-only [cmds]
  (doseq [cmd (split cmds #"; ")]
    (println (str "=> " cmd))))

(defn get-converge-conf [config servers tags args]
  (loop [s (filter-servers servers tags)
         acc []]
    (if (seq s)
      (let [cmds (for [t (filter-tags config tags)
                       f (:fns (second t))
                       :let [args (merge (:config (second t))
                                         (:config (first s))
                                         args)]]
                   {:tag (first t) :cmd (get-cmd args f)})]
        (recur (rest s)
               (conj acc
                     (merge (first s) {:cmds cmds}))))
      acc)))

(comment (get-converge-conf tags-config (env :servers) [:uat :app-server] {:say {:hello "from converge"}}))

(defn dry-run [config servers tags args]
  (let [to-run (get-converge-conf config servers tags args)]
    (newline)
    (println (str "Converging servers containing the following tags : " (print-str tags)))
    (doseq [s to-run
            cmd (-> s :cmds)]
      (newline)
      (println "###############################################################################################################")
      (println (str "Converging server - " (:name s) " - with config for tag: " (name (:tag cmd))))
      (println "###############################################################################################################")
      (newline)
      (print-cmd-only (:cmd cmd)))))

;; (defn print-server-run []
;;   (doseq [s to-run
;;           cmd (-> s :cmds)]
;;     (newline)
;;     (println "###############################################################################################################")
;;     (println (str "Converging server - " (:name s) " - with config for tag: " (name (:tag cmd))))
;;     (println "###############################################################################################################")
;;     (newline)
;;     (let [cmd (:cmd cmd)
;;           {:keys [exit] :as result} (run-on-server s cmd)]
;;       (print-cmd-run cmd result)
;;       (when (not= 0 exit) (throw+ {:cmd cmd :type ::cmd-run-error})))))

(defn converge [config servers tags args]
  (let [to-run (get-converge-conf config servers tags args)]
    (newline)
    (println (str "Converging servers containing the following tags : " (print-str tags)))
    (try+
     (doseq [s to-run
             cmd (-> s :cmds)]
       (newline)
       (println "###############################################################################################################")
       (println (str "Converging server - " (:name s) " - with config for tag: " (name (:tag cmd))))
       (println "###############################################################################################################")
       (newline)
       (let [cmd (:cmd cmd)
             {:keys [exit] :as result} (run-on-server s cmd)]
         (print-cmd-run cmd result)
         (when (not= 0 exit) (throw+ {:cmd cmd :type ::cmd-run-error}))))
     (catch [:type ::cmd-run-error] {:keys [cmd]}
       (println "Exiting the convergence run as error occurred.")))))



(comment (converge tags-config (env :servers) [:uat :app-server] {:say {:hello "from converge"}}))
