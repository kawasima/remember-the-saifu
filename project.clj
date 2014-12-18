(defproject net.unit8/remember-the-saifu "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.2.2"]
                 [hiccup "1.0.5"]
                 [liberator "0.12.2"]
                 [org.clojure/clojurescript "0.0-2371"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [sablono "0.2.22"]
                 [prismatic/om-tools "0.3.6"]
                 [garden "1.2.5"]
                 [om "0.7.3"]]
  :source-paths ["src/clj"]
  :plugins [[lein-ring "0.8.13"]
            [lein-cljsbuild "1.0.3"]]
  :ring {:handler remember-the-saifu.core/app}

  :cljsbuild {:builds
               [{:id "dev"
                 :source-paths ["src/cljs"]
                 :compiler {:output-to "resources/public/js/main.js"
                            :optimizations :simple}}
                {:id "production"
                 :source-paths ["src/cljs"]
                 :compiler {:output-to "resources/public/js/main.min.js"
                            :optimizations :advanced
                            :pretty-print false
                            :preamble ["react/react.min.js"]
                            :externs ["react/externs/react.js"
                                      "externs/google_maps_api_v3.js"]}}]}
  :profiles {:dev {:jvm-opts ["-Djava.library.path=/usr/local/share/OpenCV/java"]
                   :dependencies [[opencv "2.4.10"]]}
             :production {:jvm-opts ["-Djava.library.path=/usr/lib/jni"]
                          :dependencies [[opencv "2.4.8"]]}})
