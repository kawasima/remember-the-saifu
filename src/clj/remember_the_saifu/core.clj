(ns remember-the-saifu.core
  (:use [compojure.core :only [defroutes GET POST ANY]]
        [liberator.core :only [defresource request-method-in]]
        [liberator.representation :only [Representation]]
        [hiccup.middleware :only [wrap-base-url]]
        [hiccup core page element])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :as response]
            [garden.core :refer [css]]
            [garden.units :refer [px]])
  (:import [org.opencv.core Core Mat MatOfInt MatOfFloat Size CvType]
           [org.opencv.highgui Highgui]
           [org.opencv.imgproc Imgproc]
           [java.util Arrays]))

(clojure.lang.RT/loadLibrary  org.opencv.core.Core/NATIVE_LIBRARY_NAME)

(def api-key "AIzaSyDRLGQ_juGgfSjy4xHV1grDEvdAzTsUEzo")
(def hist-size (MatOfInt. (int-array [25])))
(def hist-range (MatOfFloat. (float-array [0 256])))

(defn saifu-css []
  (css {:vendors ["webkit" "moz" "o"]
        :auto-prefix #{:background-size}}
       [:#saifu-canvas {:width "100%"}]
       [:#map-canvas {:width "100%"}]
       [:#splash {:background-size "cover"
                  :width "100%" :height "100%" :min-height "100%"
                  :padding-top (px 40)
                  :background "url(img/redking-logo.png) no-repeat center center fixed"}]
       [:.padding {:padding-left "10%" :padding-right "10%"}]
       [:.full {:width "100%" :height "100%" :min-height "100%"}]))

(defn index []
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    (include-css "css/semantic.min.css" "saifu.css")]
   [:body
    [:audio#voice-saifu
     [:source {:src "mp3/saifu.mp3" :type "audio/x-mp3"}]]
    [:div#app.full]
    (include-js "react/react.js"
                (str "https://maps.googleapis.com/maps/api/js?key=" api-key)
                "js/main.js")
    (javascript-tag "goog.require('remember-the-saifu.core');")]))

(defroutes main-routes
  (GET "/" [] (index))
  (POST "/upload_saifu" {{{model :tempfile} :model {scene :tempfile} :scene} :params}
    (let [img-model (Highgui/imread (.getPath model) Highgui/CV_LOAD_IMAGE_COLOR)
          img-scene (Highgui/imread (.getPath scene) Highgui/CV_LOAD_IMAGE_COLOR)
          img-resized (Mat.)
          hist-model (Mat.)
          hist-scene (Mat.)]
      (Imgproc/resize img-scene img-resized (Size. (.width img-model) (.height img-model)))
      (.convertTo img-model img-model CvType/CV_32F)
      (.convertTo img-resized img-resized CvType/CV_32F)
      (Imgproc/calcHist (Arrays/asList (object-array [img-model])) (MatOfInt. (int-array [0])) (Mat.)   hist-model hist-size hist-range)
      (Imgproc/calcHist (Arrays/asList (object-array [img-resized])) (MatOfInt. (int-array [0])) (Mat.) hist-scene hist-size hist-range)
      {:content-type "application/json"
       :body (str "{\"score\":"
                  (Imgproc/compareHist hist-model hist-scene Imgproc/CV_COMP_CORREL)
                  "}")}))
  (GET "/react/react.js" [] (response/resource-response "react/react.js"))
  (GET "/saifu.css" [] {:content-type "text/css"
                        :body (saifu-css)})
  (route/resources "/"))

(def app
  (-> (handler/site main-routes)
      (wrap-base-url)))
