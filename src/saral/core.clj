(ns saral.core
  (:require [saral.ssh :refer [get-agent run-cmds]]
            [clojure.string :refer [join split trim blank?]]
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
                               :args {:say {:hello "from run time config"}}}})

(defn filter-tags [tags-coll tags]
  (select-keys tags-coll tags))

(comment (filter-tags tags-config [:uat :app-server]))

(defn print-cmd-run [{:keys [cmd error out exit-code err-msg] :or {err-msg ""}}]
  (doseq [v (split cmd #"; ")]
    (println (str "=> " v)))
  (println "---------------------------------------------------------------------------------------------------------------")
  (if error
    (do
      (println "ERROR")
      (println "Exit Code - " exit-code)
      (when-not (blank? out) (println (trim out)))
      (println "Error Message - " (trim err-msg)))
    (do
      (println (trim out))
      (newline))))

(comment (print-cmd-run {:cmd "ls",
                         :error false,
                         :out "faiz-static\nfaiz.service\n",
                         :exit-code 0})
         (print-cmd-run {:cmd "hello",
                         :error true,
                         :out "",
                         :exit-code 127,
                         :err-msg "bash: hello: command not found\n"}))

(defn print-cmd-only [cmds]
  (doseq [cmd (split cmds #"; ")]
    (println (str "=> " cmd))))

(defn create-config [config servers tags args]
  (loop [s (filter-servers servers tags)
         acc []]
    (if (seq s)
      (let [cmds (for [t (filter-tags config tags)
                       f (:fns (second t))
                       :let [args (merge (:args (second t))
                                         (:args (first s))
                                         args)]]
                   {:tag (first t) :cmd (get-cmd args f)})]
        (recur (rest s)
               (conj acc
                     (merge (first s) {:cmds cmds}))))
      acc)))

(comment (create-config tags-config (env :servers) [:uat :app-server] {:say {:hello "from converge"}}))

(defn dry-run [config servers tags args]
  (let [to-run (create-config config servers tags args)]
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

(defn print-server-run [s]
  (newline)
  (println "###############################################################################################################")
  (println (str "Applying config on server - " (:name s)))
  (println "###############################################################################################################")
  (newline)
  (doseq [v (:cmds s)]
    (print-cmd-run v)))

(comment (print-server-run {:tags [:uat :app-server],
                            :ip "52.0.104.167",
                            :name "UAT App server",
                            :cmds
                            [{:cmd "ls",
                              :error false,
                              :out "faiz-static\nfaiz.service\n",
                              :exit-code 0}
                             {:cmd "hello",
                              :error true,
                              :out "",
                              :exit-code 127,
                              :err-msg "bash: hello: command not found\n"}],
                            :identity-file "~/.ssh/faiz_hamza.pem",
                            :user "core"}))

(defn apply-config [config servers tags args]
  (newline)
  (println (str "Applying config on servers containing the following tags : " (apply str tags)))
  (->> (create-config config servers tags args)
       (map run-cmds)
       (map print-server-run)))

(comment (apply-config tags-config (env :servers) [:uat :app-server] {:say {:hello "from converge"}}))
