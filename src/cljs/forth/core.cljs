(ns forth.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]
   [clojure.string :as str]
   [instaparse.core :as insta :refer-macros [defparser]]))

;;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; Page components

(defn restn [x n] (nth (iterate rest x) n))

(defn apply-binary-to-stack [f stack]
  (->> (f (nth stack 1) (nth stack 0))
       (conj (restn stack 2))))

(defn long-str [& strings] (str/join "\n" strings))

(defparser forth-parse
           (long-str "output =  sequence"
                     "list = <'['> sequence <']'>"
                     "<sequence> = <[whitespace]> [element] (<whitespace> element)* <[whitespace]>"
                     "<element> = word | number | list"
                     "whitespace = #'\\s+'"
                     "word = ('\\'') | (#'[a-zA-Z\\+\\-\\\\*\\<\\>=]'+)"
                     "number = '0' | (#'[1-9]' #'[0-9]'*)"))

(defn forth-transform [parsed]
  (->> parsed
       (insta/transform {:list list
                         :word str
                         :number (comp js/parseFloat str)})
       (seq)
       (rest)))

(defn operation-single-return [stack n op]
  (->> stack
       (take n)
       (apply op)
       (conj (nthrest stack n))))

(defn operation-binary [stack op]
  (operation-single-return stack 2
                           (fn [x y]
                             (op y x))))

(defn operation-multiple-return [stack n op]
  (as-> stack x
        (take n x)
        (apply op x)
        (concat x (nthrest stack n))))

(def program-output (atom '[]))

(defn forth-eval [start-program start-vars start-stack]
  (loop [program start-program
         vars start-vars
         stack start-stack]
    (if (empty? program)
      (list stack vars)
      (let [instruction (first program)]
        ;(print instruction vars stack)
        (cond
          (or (seq? instruction) (number? instruction) )
            (recur (rest program) vars (conj stack instruction))
          (string? instruction)
            (case instruction
              "print" (do
                        (swap! program-output #(conj % (first stack)))
                        (recur (rest program) vars (rest stack)))
              "+"   (recur (rest program) vars (operation-binary stack +))
              "-"   (recur (rest program) vars (operation-binary stack -))
              "mod"   (recur (rest program) vars (operation-binary stack mod))
              "="   (recur (rest program) vars (operation-binary stack =))
              ">"   (recur (rest program) vars (operation-binary stack >))
              "<"   (recur (rest program) vars (operation-binary stack <))
              "and" (recur (rest program) vars (operation-binary stack #(and %1 %2)))
              "or"  (recur (rest program) vars (operation-binary stack #(or %1 %2)))
              "not" (recur (rest program) vars (conj (rest stack) (not (first stack))))

              "true" (recur (rest program) vars (conj stack true))
              "false" (recur (rest program) vars (conj stack false))

              "choose" (recur (rest program) vars (operation-single-return stack 3 #(if %1 %2 %3)))
              "break-if" (if (first stack)
                           (list (rest stack) vars)
                           (recur (rest program) vars (rest stack)))

              "del" (recur (rest program) vars (rest stack))
              "dup" (recur (rest program) vars (operation-multiple-return stack 1 #(list % %)))
              "swap" (recur (rest program) vars (operation-multiple-return stack 2 #(list %2 %1)))
              "push" (recur (rest program) vars (operation-binary stack conj))
              "pop" (recur (rest program) vars (operation-multiple-return stack 1 #(list (first %) (rest %))))
              "concat" (recur (rest program) vars (operation-binary stack #(concat %2 %1)))


              "collect" (recur (rest program) vars (list stack))
              "collect-n" (recur (rest program) vars
                                 (let [n (first stack)]
                                   (conj (nthrest stack (+ n 1)) (take n (rest stack)))))
              "drop" (recur (rest program) vars (concat (first stack) (rest stack)))

              "'" (recur (nthrest program 2) vars (conj stack (nth program 1)))
              "defproc" (recur (rest program)
                               (assoc vars (first stack) (nth stack 1))
                               (nthrest stack 2))
              "eval" (let [[new-stack new-vars] (forth-eval (first stack) vars (rest stack))]
                       (recur (rest program) new-vars new-stack))
              (if (contains? vars instruction)
                (let [[new-stack new-vars] (forth-eval (get vars instruction) vars stack)]
                  (recur (rest program) new-vars new-stack))
                (throw (ex-info "Undefined variable" {:variable-symbol instruction
                                                      :all-variables (keys vars)})))))))))

(defn editor-input [value]
  [:textarea {:value @value
              :on-change (fn [e]
                           (let [new-value (-> e .-target .-value)]
                             (reset! value new-value)
                             (.setItem (.-localStorage js/window) :src new-value)))}])


(defn stack-element [stack]
  (str/join ", " stack))

(defn display-output [result elem-type]
  [:span
   (for [item result]
     (cond
       (seq? item) [elem-type "[ " (display-output item :span) "] "]
       (boolean? item) [elem-type (if item ":true" ":false") " "]
       :else [elem-type item " "]))])

(defn editor-component []
  (let [result (atom '())
        program (atom (.getItem (.-localStorage js/window) :src))]
    (fn []
      [:form {:on-submit #(do
                            (print "Running program...\n\n")
                            (.preventDefault %)
                            (reset! result (-> @program
                                               (forth-parse)
                                               (forth-transform)
                                               (forth-eval {} '())
                                               (first)
                                               )))}

       [editor-input program]
       [:br]
       [:input {:type "submit"
                :value "Run"}]
       [:br]
       [:h5 "Final stack:"]
       [display-output @result :span#output-span]
       [:br]
       [:h5 "Output:"]
       [:div#output (display-output @program-output :p#output-p)]])))

(defn home-page []
  (fn []
    [:span.main
     [:h1 "tiny forth"]
     [editor-component]]))


;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page))


;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p [:a {:href (path-for :index)} "Home"]]]
       [page]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (rdom/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
