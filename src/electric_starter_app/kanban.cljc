(ns electric-starter-app.kanban
  (:require [clojure.string :as str]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :as forms5]))

(def columns ["Todo" "Doing" "Done"])

(defn empty-room []
  {:cards {} :order (zipmap columns (repeat []))})

#?(:clj (defonce !rooms (atom {})))

#?(:clj
   (defn get-room [rooms slug]
     (or (get rooms slug) (empty-room))))

#?(:clj
   (defn ensure-room [rooms slug]
     (update rooms slug #(or % (empty-room)))))

#?(:clj
   (defn add-card! [slug column]
     (swap! !rooms
       (fn [rooms]
         (let [rooms (ensure-room rooms slug)
               id (str (random-uuid))]
           (-> rooms
               (assoc-in [slug :cards id] {:title "" :assignee "" :description ""})
               (update-in [slug :order column] (fnil conj []) id)))))
     nil))

#?(:clj
   (defn delete-card! [slug card-id]
     (swap! !rooms
       (fn [rooms]
         (if (contains? rooms slug)
           (-> rooms
               (update-in [slug :cards] dissoc card-id)
               (update-in [slug :order] update-vals #(vec (remove #{card-id} %))))
           rooms)))
     nil))

#?(:clj
   (defn edit-field! [slug card-id field v]
     (swap! !rooms
       (fn [rooms]
         (if (get-in rooms [slug :cards card-id])
           (assoc-in rooms [slug :cards card-id field] v)
           rooms)))
     nil))

#?(:clj
   (defn move-card! [slug card-id target-col before-card-id]
     (swap! !rooms
       (fn [rooms]
         (if (get-in rooms [slug :cards card-id])
           (let [rooms  (ensure-room rooms slug)
                 order  (get-in rooms [slug :order])
                 order' (update-vals order #(vec (remove #{card-id} %)))
                 col-ids (or (get order' target-col) [])
                 idx    (if before-card-id
                          (let [i (.indexOf col-ids before-card-id)]
                            (if (neg? i) (count col-ids) i))
                          (count col-ids))
                 col-ids' (vec (concat (subvec col-ids 0 idx) [card-id] (subvec col-ids idx)))]
             (assoc-in rooms [slug :order] (assoc order' target-col col-ids')))
           rooms)))
     nil))

(e/defn TextArea [v]
  (e/client
    (dom/textarea
      (dom/props {:rows 3 :placeholder "description" :class "card-description"})
      (when-not (dom/Focused?)
        (set! (.-value dom/node) (str v)))
      (dom/On "input" #(-> % .-target .-value) (str v)))))

(e/defn CardView [slug col card-id card]
  (e/client
    (let [{:keys [title assignee description]} card]
      (dom/div
        (dom/props {:class "card" :draggable true})
        (dom/On "dragstart"
          (fn [e]
            (.setData (.-dataTransfer e) "text/plain" (str card-id))
            (set! (.. e -dataTransfer -effectAllowed) "move")
            nil) nil)
        (dom/On "dragover" (fn [e] (.preventDefault e) nil) nil)
        (let [dropped (dom/On "drop"
                        (fn [e]
                          (.preventDefault e)
                          (.stopPropagation e)
                          (let [id (.getData (.-dataTransfer e) "text/plain")]
                            (when (and (seq id) (not= id (str card-id)))
                              id))) nil)]
          (when dropped
            (e/server (move-card! slug dropped col card-id))))
        (let [v (forms5/Input title :maxlength 200 :placeholder "title" :class "card-title")]
          (when (not= v title)
            (e/server (edit-field! slug card-id :title v))))
        (let [v (forms5/Input assignee :maxlength 100 :placeholder "assignee" :class "card-assignee")]
          (when (not= v assignee)
            (e/server (edit-field! slug card-id :assignee v))))
        (let [v (TextArea description)]
          (when (not= v description)
            (e/server (edit-field! slug card-id :description v))))
        (dom/button (dom/props {:class "card-delete" :title "Delete card"})
          (dom/text "×")
          (let [click (dom/On "click" (fn [e] (.stopPropagation e) true) false)]
            (when click
              (e/server (delete-card! slug card-id)))))))))

(e/defn ColumnView [slug col card-ids cards]
  (e/client
    (dom/div (dom/props {:class "column"})
      (dom/h2 (dom/text col))
      (dom/div (dom/props {:class "column-body"})
        (dom/On "dragover" (fn [e] (.preventDefault e) nil) nil)
        (let [dropped (dom/On "drop"
                        (fn [e]
                          (.preventDefault e)
                          (let [id (.getData (.-dataTransfer e) "text/plain")]
                            (when (seq id) id))) nil)]
          (when dropped
            (e/server (move-card! slug dropped col nil))))
        (e/for [card-id (e/diff-by identity card-ids)]
          (CardView slug col card-id (get cards card-id))))
      (dom/button (dom/props {:class "add-card"}) (dom/text "+ Add card")
        (let [click (dom/On "click" identity nil)]
          (when click
            (e/server (add-card! slug col))))))))

(defn slug-from-pathname []
  #?(:cljs (let [path (.-pathname js/location)
                 seg (-> (subs path 1) (str/replace #"/.*$" ""))]
             (if (str/blank? seg) "default" seg))))

(declare css)

(e/defn Kanban []
  (e/client
    (dom/style (dom/text css))
    (let [slug  (slug-from-pathname)
          room  (e/server (get-room (e/watch !rooms) slug))
          order (:order room)
          cards (:cards room)]
      (dom/h1 (dom/text "Kanban — room: " slug))
      (dom/p (dom/props {:class "hint"})
        (dom/text "Open the same URL in another tab to collaborate; change the path to join a different room."))
      (dom/div (dom/props {:class "board"})
        (ColumnView slug "Todo"  (get order "Todo")  cards)
        (ColumnView slug "Doing" (get order "Doing") cards)
        (ColumnView slug "Done"  (get order "Done")  cards)))))

(def css "
.board { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; padding: 1rem; }
.column { background: #e2e8f0; border-radius: 6px; padding: 0.5rem; display: flex; flex-direction: column; min-height: 6rem; }
.column h2 { margin: 0.25rem 0.5rem 0.5rem; font-size: 1rem; }
.column-body { flex: 1; display: flex; flex-direction: column; gap: 0.5rem; min-height: 3rem; padding: 0.25rem; }
.card { background: white; border-radius: 4px; padding: 0.5rem; box-shadow: 0 1px 2px rgba(0,0,0,0.08); position: relative; cursor: grab; display: flex; flex-direction: column; gap: 0.25rem; }
.card:active { cursor: grabbing; }
.card input, .card textarea { width: 100%; border: 1px solid #cbd5e1; border-radius: 3px; padding: 0.2rem 0.35rem; font: inherit; font-size: 0.9rem; resize: vertical; }
.card-title { font-weight: 600; }
.card-delete { position: absolute; top: 0.25rem; right: 0.25rem; background: transparent; border: none; cursor: pointer; font-size: 1rem; line-height: 1; color: #64748b; }
.card-delete:hover { color: #b91c1c; }
.add-card { margin: 0.5rem 0.25rem 0.25rem; background: transparent; border: 1px dashed #94a3b8; padding: 0.4rem; border-radius: 4px; cursor: pointer; color: #475569; }
.add-card:hover { background: white; }
h1 { padding: 0 1rem; margin: 0.75rem 0 0.25rem; font-size: 1.25rem; }
.hint { padding: 0 1rem; color: #64748b; font-size: 0.85rem; margin: 0 0 0.5rem; }
")
