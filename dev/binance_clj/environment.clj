(ns binance-clj.environment
  "Build-time validation for the project's required Java runtime.")

(def ^:private required-java-major
  "Required Java feature release for local development and CI."
  25)

(defn- java-major
  [version]
  (let [major-token (first (.split version "\\."))]
    (parse-long major-token)))

(defn -main
  "Exits unsuccessfully unless the project is running on JDK 25."
  [& _args]
  (let [version (System/getProperty "java.version")
        major (java-major version)]
    (if (= required-java-major major)
      (println (str "Environment OK: Java " version ", Clojure " (clojure-version)))
      (do
        (binding [*out* *err*]
          (println (str "Environment error: JDK " required-java-major
                        " is required; current Java is " version ".")))
        (System/exit 1)))))
