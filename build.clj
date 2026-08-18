(ns build
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deps-deploy]))

(def ^:private lib 'io.github.ugurbay/binance-clj)
(def ^:private class-dir "target/classes")
(def ^:private target-dir "target")
(def ^:private source-dirs ["src"])
(def ^:private resource-dirs ["resources"])
(def ^:private repository-url "https://github.com/ugurbay/clojure-binance-connector")
(def ^:private basis (delay (b/create-basis {:project "deps.edn"})))

(defn- release-version []
  (let [value (str/trim (slurp "VERSION"))]
    (when-not (re-matches #"[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?" value)
      (throw (ex-info "VERSION must contain a Maven-safe semantic version."
                      {:version value})))
    value))

(defn- jar-path [version]
  (format "%s/%s-%s.jar" target-dir (name lib) version))

(defn- pom-options [version]
  {:basis @basis
   :src-pom :none
   :class-dir class-dir
   :lib lib
   :version version
   :src-dirs source-dirs
   :resource-dirs resource-dirs
   :scm {:connection (str "scm:git:" repository-url ".git")
         :developerConnection
         "scm:git:ssh://git@github.com/ugurbay/clojure-binance-connector.git"
         :tag (str "v" version)
         :url repository-url}
   :pom-data
   [[:description
     "A safe, data-oriented Clojure connector for Binance Spot REST and WebSocket APIs."]
    [:url repository-url]
    [:licenses
     [:license
      [:name "MIT License"]
      [:url "https://opensource.org/license/mit/"]
      [:distribution "repo"]]]
    [:developers
     [:developer
      [:id "ugurbay"]
      [:name "ugurbay"]
      [:url "https://github.com/ugurbay"]]]]})

(defn clean
  "Delete generated package output."
  [opts]
  (b/delete {:path target-dir})
  opts)

(defn jar
  "Build the source JAR and embedded Maven POM for the version in VERSION."
  [opts]
  (let [version (release-version)
        artifact (jar-path version)]
    (clean opts)
    (b/write-pom (pom-options version))
    (b/copy-dir {:src-dirs (into source-dirs resource-dirs)
                 :target-dir class-dir})
    (b/copy-file {:src "LICENSE"
                  :target (str class-dir "/META-INF/LICENSE")})
    (b/copy-file {:src "NOTICE.en.md"
                  :target (str class-dir "/META-INF/NOTICE.md")})
    (b/jar {:class-dir class-dir
            :jar-file artifact
            :manifest {"Implementation-Title" "binance-clj"
                       "Implementation-Version" version}})
    (assoc opts :lib lib :version version :jar-file artifact)))

(defn install
  "Build and install the package into the local Maven repository."
  [opts]
  (let [{:keys [version jar-file] :as result} (jar opts)]
    (b/install {:basis @basis
                :lib lib
                :version version
                :jar-file jar-file
                :class-dir class-dir})
    result))

(defn deploy
  "Build and deploy the package to Clojars using process-local credentials."
  [opts]
  (doseq [variable ["CLOJARS_USERNAME" "CLOJARS_PASSWORD"]]
    (when (str/blank? (System/getenv variable))
      (throw (ex-info (str variable " must be set in the current process.")
                      {:missing-environment-variable variable}))))
  (let [{:keys [jar-file] :as result} (jar opts)]
    (deps-deploy/deploy
     {:installer :remote
      :artifact (str (b/resolve-path jar-file))
      :pom-file (str (b/pom-path {:class-dir class-dir :lib lib}))
      :sign-releases? false})
    result))
