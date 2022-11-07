(ns electron.context-menu
  (:require [clojure.string :as string]
            [electron.utils :as utils]
            ["electron" :refer [Menu MenuItem shell] :as electron]
            ["electron-dl" :refer [download]]))

;; context menu is registerd in window/setup-window-listeners!
(defn setup-context-menu!
  [^js win]
  (let [web-contents (.-webContents win)

        context-menu-handler
        (fn [_event ^js params]
          (let [menu (Menu.)
                suggestions (.-dictionarySuggestions params)
                edit-flags (.-editFlags params)
                editable? (.-isEditable params)
                selection-text (.-selectionText params)
                has-text? (not (string/blank? (string/trim selection-text)))
                link-url (not-empty (.-linkURL params))
                media-type (.-mediaType params)]

            (doseq [suggestion suggestions]
              (. menu append
                 (MenuItem. (clj->js {:label
                                      suggestion
                                      :click
                                      #(. web-contents replaceMisspelling suggestion)}))))
            (when-let [misspelled-word (not-empty (.-misspelledWord params))]
              (. menu append
                 (MenuItem. (clj->js {:label
                                      "Add to dictionary"
                                      :click
                                      #(.. web-contents -session (addWordToSpellCheckerDictionary misspelled-word))})))
              (. menu append (MenuItem. #js {:type "separator"})))

            (when (and utils/mac? has-text? (not link-url))
              (. menu append
                 (MenuItem. #js {:label (str "Look Up “" selection-text "”")
                                 :click #(. web-contents showDefinitionForSelection)})))

          ;;这是右键菜单

            (when has-text?
              (. menu append
                 (MenuItem. #js {:label "用Google搜索"
                                 :click #(let [url (js/URL. "https://www.google.com/search")]
                                           (.. url -searchParams (set "q" selection-text))
                                           (.. shell (openExternal (.toString url))))}))
              (. menu append (MenuItem. #js {:type "separator"})))


            (when editable?
              (when has-text?
                (. menu append
                   (MenuItem. #js {:label "剪切"
                                   :enabled (.-canCut edit-flags)
                                   :role "cut"}))
                (. menu append
                   (MenuItem. #js {:label "复制"
                                   :enabled (.-canCopy edit-flags)
                                   :role "copy"})))

              (. menu append
                 (MenuItem. #js {:label "粘贴"
                                 :enabled (.-canPaste edit-flags)
                                 :role "paste"}))
              (. menu append
                 (MenuItem. #js {:label "全部选中"
                                 :enabled (.-canSelectAll edit-flags)
                                 :role "selectAll"})))

            (when (= media-type "image")
              (. menu append
                 (MenuItem. #js {:label "Save Image"
                                 :click (fn [menu-item]
                                          (let [url (.-srcURL params)
                                                url (if (.-transform menu-item)
                                                      (. menu-item transform url)
                                                      url)]
                                            (download win url)))}))

              (. menu append
                 (MenuItem. #js {:label "Save Image As..."
                                 :click (fn [menu-item]
                                          (let [url (.-srcURL params)
                                                url (if (.-transform menu-item)
                                                      (. menu-item transform url)
                                                      url)]
                                            (download win url #js {:saveAs true})))}))

              (. menu append
                 (MenuItem. #js {:label "Copy Image"
                                 :click #(. web-contents copyImageAt (.-x params) (.-y params))})))

            (when (not-empty (.-items menu))
              (. menu popup))))]

    (doto web-contents
      (.on "context-menu" context-menu-handler))

    context-menu-handler))
