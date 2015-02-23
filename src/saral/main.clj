(ns saral.main
  (:require [saral.core :refer [converge]]
            [environ.core :refer [env]]))

(def tags-config {:uat {:fns [["echo uat!!"]]}
                  :prod {:fns [["echo prod!!"]]}
                  :app-server {:fns [["cd /" "ls"]
                                     ["echo hello {{say.hello}}"]]
                               :args {:say {:hello "from args"}}}})

(comment (converge tags-config (env :servers) [:uat :app-server] {:say {:hello "from converge"}}))

