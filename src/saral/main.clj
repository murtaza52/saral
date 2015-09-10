(ns saral.main
  (:require [saral.core :refer [apply-config dry-run create-config]]
            [environ.core :refer [env]]))

(def tags-config {:uat {:fns [["echo uat!!"] ["error me"]]}
                  :prod {:fns [["echo prod!!"]]}
                  :app-server {:fns [["cd /" "ls"]
                                     ["echo hello {{say.hello}}"]]
                               :args {:say {:hello "from run time config"}}}})

(comment
  (create-config tags-config (env :servers) [:uat] [:app-server] {:say {:hello "from converge"}})
  (dry-run tags-config (env :servers) [:uat :app-server] {:say {:hello "from converge"}})
  (apply-config tags-config (env :servers) [:uat] {:say {:hello "from converge"}}))

