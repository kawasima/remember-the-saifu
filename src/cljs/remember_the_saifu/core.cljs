(ns remember-the-saifu.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! chan pub sub]]
            [clojure.browser.net :as net]
            [goog.events :as events]
            [goog.Timer :as timer])
  (:import [goog.net.EventType]
           [goog.events EventType]
           [google.maps.Map]))

(enable-console-print!)

(defn update-position [ch marker]
  (.. js/navigator -geolocation
      (getCurrentPosition
       (fn [position]
         (let [latlng (google.maps.LatLng. (.. position -coords -latitude)
                                           (.. position -coords -longitude))]
           (.setPosition marker latlng)
           (put! ch latlng))))))

(defcomponent map-view [app owner]
  (render
   [_]
   (html
    (if (:geolocation? app)
      [:div#map-canvas.full]
      [:div "GeoLocation isn't available."])))
  (did-mount
    [_]
    (when-let [map-canvas (.getElementById js/document "map-canvas")]
      (let [center (:center-position app)
            map (google.maps.Map. map-canvas
                                  (clj->js {:center center :zoom 16}))
            ch (chan)]
        (google.maps.Circle.
         (clj->js {:strokeColor "#00ff00"
                   :strokeOpacity 0.8
                   :strokeWeight 2
                   :fillColor "#00ff00"
                   :fillOpacity 0.35
                   :map map
                   :center center
                   :radius 300}))
        (go
          (let [latlng (<! ch)
                marker (google.maps.Marker.
                        (clj->js {:position latlng
                                  :icon "img/redking.png"
                                  :map map
                                  :title "redking"}))
                timer (goog.Timer. 15000)]
            (.start timer)
            (events/listen timer goog.Timer/TICK #(update-position ch marker))
            (while true
              (let [current (<! ch)
                    distance (google.maps.geometry.spherical.computeDistanceBetween current (.getCenter map))]
                (om/update! app :outside? (> distance 300))))))
        
        (.. js/navigator -geolocation
            (getCurrentPosition (fn [position]
                                  (put! ch (google.maps.LatLng. (.. position -coords -latitude)
                                                                (.. position -coords -longitude))))))))))

(defn upload-saifu [e app model]
  (let [xhrio (net/xhr-connection)]
    (events/listen xhrio goog.net.EventType.SUCCESS
                   (fn [e]
                     (om/update! app :have-saifu
                                 (if (> (get (js->clj (.getResponseJson xhrio)) "score") 0.7)
                                   :ok
                                   "それ君の財布ちゃうでー"))))
    (let [form (js/FormData.)]
      (.append form "scene" (-> js/document
                                (.getElementById  "saifu-image")
                                .-files
                                (aget 0)))
      (.append form "model" model)
      (.send xhrio (str "upload_saifu") "post"
             form))))

(defn register-saifu [e owner]
  (let [reader (js/FileReader.)
        file (-> js/document
                 (.getElementById "saifu-image")
                 .-files
                 (aget 0))]
    (set! (.-onload reader)
          (fn [e] (om/set-state! owner :img (.. e -target -result))))
    (.readAsDataURL reader file)
    (om/set-state! owner :saifu-image-file file)))

(defcomponent register-saifu-view [app owner]
  (render-state [_ {:keys [img saifu-image-file]}]
    (html
     [:div.ui.raised.segment
      (if img
        [:div.ui.grid
         [:div.sixteen.wide.column.center
          [:canvas#saifu-canvas]]
         [:div.sixteen.wide.column.center
          [:div.ui.buttons
          [:button.ui.button
           {:on-click #(om/set-state! owner :img nil)}
           "ちゃうで！"]
          [:div.or]
          [:button.ui.positive.button
           {:on-click (fn [e]
                        (om/update! app :saifu-image saifu-image-file))}
           "これだっ！"]]]]
        
        [:div.ui.grid
         [:div.sixteen.wide.column
          [:h4.ui.pink.header "Remember the SAIFU"]]
         [:div.sixteen.wide.column
          [:div.ui.info.message
           [:div.header "まず財布の登録や"]
           [:input#saifu-image {:type "file" :accept "image/*;capture=camera" :name "saifu_image"
                                :on-change #(register-saifu % owner)}]]]])]))
  (did-update
   [_ _ _]
   
   (if-let [img (om/get-state owner :img)]
     (let [canvas (.getElementById js/document "saifu-canvas")
           context (.getContext canvas "2d")
           image-obj (js/Image.)]
       (set! (.-onload image-obj)
             #(this-as this (.drawImage context this 0 0 (.-width canvas) (.-height canvas))))
       (set! (.-src image-obj) img)))))

(defcomponent main-view [app owner]
  (render [_]
    (html
     (if-let [saifu-image (:saifu-image app)]
       [:div.full
        (om/build map-view app)
        (when (:outside? app)
          [:div#map-overlay.ui.dimmer.page.visible.active.full
           [:div.content
            [:div.center.padding
             (if (= (:have-saifu app) :ok) 
               [:div.ui.success.message
                [:div.header "よし、呑みいこ"]
                [:div.center
                 [:img.ui.avatar.image {:src "http://cdn-ak.f.st-hatena.com/images/fotolife/t/tenten0213/20130824/20130824022138.jpg"}]]]
               [:div.ui.info.message
                [:i.wallet.icon]
                [:div.header
                 (if-let [msg (:have-saifu app)]
                   msg
                   "さいふ見せて")]
                [:input#saifu-image {:type "file" :accept "image/*;capture=camera" :name "saifu_image"
                                   :on-change #(upload-saifu % app saifu-image)}]])]]])]
       [:div#splash.padding
        (om/build register-saifu-view app)])))
  (did-update
   [_ _ _]
   (when-not (= (:have-saifu @app) :ok)
     (.. (.getElementById js/document "voice-saifu") play))))

(.. js/navigator -geolocation
    (getCurrentPosition
     (fn [position]
       (om/root main-view
                {:geolocation? (. js/navigator -geolocation)
                 :outside? false
                 :center-position (google.maps.LatLng. (.. position -coords -latitude)
                                                       (.. position -coords -longitude))}
                {:target (.getElementById js/document "app")}))))
