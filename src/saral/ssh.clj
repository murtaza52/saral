(ns saral.ssh
  (:require [clj-ssh.ssh :as ssh]
            [clj-ssh.cli :as cli]))

(defn get-agent [identity-file]
  (if identity-file
    (let [agent (ssh/ssh-agent {:use-system-ssh-agent false})]
      (ssh/add-identity agent {:private-key-path identity-file})
      agent)
    (ssh/ssh-agent {})))

                                        ;optimize by adding memoize.
;; maybe it can be optiomized till we get session
;; explore other options of ssh
;; we may not need ton run-cmds. Just have run-cmd, give it one colon separated string.

(defn run-cmd [{:keys [ip user identity-file]
                :or {identity-file nil}}
               {:keys [cmd] :as m}]
  (let [agent (get-agent identity-file)]
    (let [session (ssh/session agent ip {:username user})]
      (ssh/with-connection session
        (let [{:keys [exit out err]} (ssh/ssh session {:cmd cmd})]
          (if (= 0 exit)
            (merge m {:error false :out out :exit-code exit}) 
            (merge m {:error true :out out :exit-code exit :err-msg err})))))))

(comment (run-cmd {:ip "52.11.233.104",
                   :user "ec2-user",
                   :identity-file "~/.ssh/techops_aws.pem"}
                  {:cmd "cd ../; ls; echo hello"}))

(defn run-cmds [s]
  (loop [cmds (:cmds s)
         acc []]
    (if-not (seq cmds)
      (assoc s :cmds acc)
      (let[result (run-cmd s (first cmds))]
        (if (:error result)
          (assoc s :cmds (conj acc result))
          (recur (rest cmds) (conj acc result)))))))

(comment (get-agent (:identity-file (-> env :servers first))))

(comment (cli/ssh "52.0.104.167" "ls" :username "core" :ssh-agent (get-agent "~/.ssh/techops_aws.pem"))) 

(comment (run-cmds {:name "UAT App server",
                    :ip "52.11.233.104",
                    :user "ec2-user",
                    :identity-file "~/.ssh/techops_aws.pem",
                    :tags [:uat :app-server]
                    :cmds '({:cmd "cd ../"}
                            {:cmd "ls"}
                            {:cmd "cd ../; ls"}
                            {:cmd "hello"}
                            {:cmd "echo hi"})}))

