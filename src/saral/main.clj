(ns saral.main
  (:require [saral.core :refer [converge dry-run get-converge-conf]]
            [environ.core :refer [env]]))

(def tags-config {:uat {:fns [["echo uat!!"] ["error me"]]}
                  :prod {:fns [["echo prod!!"]]}
                  :app-server {:fns [["cd /" "ls"]
                                     ["echo hello {{say.hello}}"]]
                               :config {:say {:hello "from run time config"}}}})

(comment
  (get-converge-conf tags-config (env :servers) [:uat :app-server] {:say {:hello "from converge"}})
  (dry-run tags-config (env :servers) [:uat :app-server] {:say {:hello "from converge"}})
  (converge tags-config (env :servers) [:uat] {:say {:hello "from converge"}}))
