(ns binance-clj.secret-scan
  "Release-gate scan for credential values accidentally copied into project files."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private included-extensions
  #{"clj" "edn" "example" "json" "md" "ps1" "yaml" "yml"})

(def ^:private ignored-directories
  #{".cache" ".cpcache" ".git" ".toolchains" "target"})

(def ^:private placeholder-markers
  ["benchmark" "example" "hidden" "placeholder" "test" "visible" "yerel"])

(def ^:private published-fixtures
  #{"vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A"})

(def ^:private suspicious-assignment
  #"(?i)(?:api[-_ ]?(?:key|secret)|private[-_ ]?key)(?:\s*[:=]\s*|\s+)['\"]([^'\"\s]{12,})['\"]")

(defn- ignored-file?
  [root file]
  (let [relative (.relativize (.toPath root) (.toPath file))
        parts (map str relative)]
    (or (= ".env" (.getName file))
        (some ignored-directories parts))))

(defn- extension
  [file]
  (some->> (.getName file) (re-find #"\.([^.]+)$") second str/lower-case))

(defn- project-files
  [root]
  (->> (file-seq root)
       (filter #(.isFile %))
       (remove #(ignored-file? root %))
       (filter #(contains? included-extensions (extension %)))))

(defn- configured-secrets
  []
  (->> ["BINANCE_API_KEY" "BINANCE_API_SECRET" "BINANCE_ED25519_PRIVATE_KEY"]
       (keep #(System/getenv %))
       (remove str/blank?)
       (filter #(<= 8 (count %)))
       distinct
       vec))

(defn- placeholder?
  [value]
  (let [lower (str/lower-case value)]
    (some #(str/includes? lower %) placeholder-markers)))

(defn- suspicious-literal?
  [text]
  (some (fn [[_ value]]
          (and (not (placeholder? value))
               (not (contains? published-fixtures value))))
        (re-seq suspicious-assignment text)))

(defn- violations
  [root files secrets]
  (keep (fn [file]
          (let [text (slurp file)
                exact-secret? (some #(str/includes? text %) secrets)]
            (when (or exact-secret? (suspicious-literal? text))
              (str (.relativize (.toPath root) (.toPath file))))))
        files))

(defn -main
  "Scans text artifacts and exits non-zero without ever printing secret values."
  [& _args]
  (let [root (.getCanonicalFile (io/file (System/getProperty "user.dir")))
        files (vec (project-files root))
        secrets (configured-secrets)
        findings (vec (violations root files secrets))]
    (println (pr-str {:credential-values-checked (count secrets)
                      :files-scanned (count files)
                      :violations (count findings)}))
    (when (seq findings)
      (binding [*out* *err*]
        (println "Secret scan failed in files:" (str/join ", " findings)))
      (System/exit 1))))
