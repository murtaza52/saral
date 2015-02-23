(ns saral.core
  (:require [clojure.string :refer [join split trim]]
            [environ.core :refer [env]]
            [clj-ssh.ssh :as ssh]
            [clj-ssh.cli :as cli]
            [clojure.set :refer [difference intersection]]
            [slingshot.slingshot :refer [throw+ try+]]
            [clostache.parser :as clo]))

(defn get-agent [identity-file]
  (if identity-file
    (let [agent (ssh/ssh-agent {:use-system-ssh-agent false})]
      (ssh/add-identity agent {:private-key-path identity-file})
      agent)
    (ssh/ssh-agent {})))

(defn run-on-server [{:keys [ip user identity-file]
                      :or {identity-file nil}}
                     command]
  (let [agent (get-agent identity-file)]
    (let [session (ssh/session agent ip {:username user})]
      (ssh/with-connection session
        (let [result (ssh/ssh session {:cmd command})]
          result)))))

(comment (get-agent (:identity-file (-> env :servers first))))

(comment (cli/ssh "52.0.104.167" "ls" :username "core" :ssh-agent (get-agent "~/.ssh/faiz_hamza.pem")))

(comment (run-on-server (-> env :servers first) "ls; echo hello"))

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

(defn converge [config servers tags args]
  (let [applicable-servers (filter-servers servers tags)
        applicable-tags (filter-tags config tags)]
    (newline)
    (println "Converging servers containing the following tags")
    (println (print-str tags))
    (try+
     (doseq [s applicable-servers r applicable-tags]
       (newline)
       (println "###############################################################################################################")
       (println (str "Converging server - " (:name s) " - with tag: " (name (first r))))
       (println "###############################################################################################################")
       (newline)
       (doseq [f (:fns (second r))
               :let [args (merge (:args (second r)) args)
                     cmd (get-cmd args f)
                     {:keys [exit] :as result} (run-on-server s cmd)]]
         (print-cmd-run cmd result)
         (when (not= 0 exit) (throw+ {:cmd cmd :type ::cmd-run-error}))))
     (catch [:type ::cmd-run-error] {:keys [cmd]}
       (println "Exiting the convergence run as error occurred.")))))

(def tags-config {:uat {:fns [["echo uat!!"]]}
                  :prod {:fns [["echo prod!!"]]}
                  :app-server {:fns [["cd /" "ls"]
                                     ["echo hello {{say.hello}}"]]
                               :args {:say {:hello "from args"}}}})

(comment (converge tags-config (env :servers) [:uat :app-server] {:say {:hello "from converge"}}))
