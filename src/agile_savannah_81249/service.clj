(ns agile-savannah-81249.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.helpers :refer [definterceptor defhandler]]
            [ring.util.response :as ring-resp]
            [io.pedestal.http :as bootstrap]
            [agile-savannah-81249.dbhelpers :as db]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [clojure.data.xml :as xml]))

(def mock-project-collection {
  :sleeping_cat {
    :name "Sleeping cat project"
    :framework "Pedestal"
    :language "Clojure"
  }
  :sleeping_dog {
    :name "Sleeping dog"
    :framework "Rails"
    :language "Ruby"
  }
  })

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))


(defn home-page
  [request]
  (let [uri "mongodb://127.0.0.1:27017/testdb"
        { :keys [conn db] } (mg/connect-via-uri uri)]
          (bootstrap/json-response
            (mc/find-maps db "project_catalog") ))
  )


(defn get_project
  [request]
  (bootstrap/json-response
    (db/db-get_project
      (get-in request [:path-params :proj-name]))
    ))

(defn post_project
  [request]
  (let [incoming (:json-params request)
        connect-string "mongodb://127.0.0.1:27017/testdb"
        { :keys [conn db]} (mg/connect-via-uri connect-string)]
    (ring-resp/created
      "http://my-created-resource-uri"
      (mc/insert-and-return db "project_catalog" incoming)
    )
  )
)

(def raw-proj-string "<project><proj-name>Cats</proj-name></project>")

(def project-xml (xml/parse-str raw-proj-string))

(defn get-by-tag [proj-map-in tname]
  (->> proj-map-in
       :content
       (filter #(= (:tag %) tname))
       first
       :content
       first
  )
)

(defn monger-mapper [xmlstring]
  "Take a raw xml string and map a know structure into a simple map"
  (let [proj-xml (xml/parse-str xmlstring)]
    {
      :proj-name (get-by-tag proj-xml :proj-name)
      :name (get-by-tag proj-xml :name)
      :framework (get-by-tag proj-xml :framework)
      :language (get-by-tag proj-xml :language)
      :repo (get-by-tag proj-xml :repo)
     }))

(defn xml-out [know-map]
  (xml/element :project {}
    (xml/element :_id {} (.toString (:_id know-map)))
    (xml/element :proj-name {} (:proj-name know-map))
    (xml/element :name {} (:name know-map))
    (xml/element :framework {} (:framework know-map))
    (xml/element :repo {} (:repo know-map))
    (xml/element :language {} (:language know-map))
  )
)

(defn post_project_xml
  [request]
  (let [uri "mongodb://127.0.0.1:27017/testdb"
        { :keys [conn db] } (mg/connect-via-uri uri)
        incoming (slurp (:body request))
        ok (mc/insert-and-return db "project_catalog" (monger-mapper incoming))
        ]
    (-> (ring-resp/created "http://resource-for-my-created-item"
          (xml/emit-str (xml-out ok)))
        (ring-resp/content-type "application/xml"))
    )
  )

(defn token-check [request]
  (let [token (get-in request [:headers "x-catalog-token"])]
    (if  (not (= token "o brave new world"))
      (assoc (ring-resp/response { :body "access denied" }) :status 403))))

(defn git-search [q]
  (let [ret
        (client/get
          (format "https://api.github.com/search/repositories?q=%s&languaje:clojure" q)
                    {:debug false
                     :content-type :json
                     :accept :json})]
    (json/read-str (ret :body)))
  )

(defn git-get
  [request]
  (bootstrap/json-response (git-search (get-in request [:query-params :q]))))

;(defn auth-token []
;  (let [ret
;        (client/post "https://jemez/auth0.com/oauth/token"
;        { :debug false
;          :content-type :json
;          :form-params { :client_id ""
;                         :client_secret ""
;                         :grand_type ""}
;                        })
;        ]
;    (json/read-str (ret :body))))

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body token-check])

;; Tabular routes
(def routes #{["/" :get (conj common-interceptors `home-page)]
              ["/about" :get (conj common-interceptors `about-page)]
              ["/projects" :post (conj common-interceptors `post_project)]
              ["/projects-xml" :post (conj common-interceptors `post_project_xml)]
              ["/projects/:proj-name" :get (conj common-interceptors `get_project)]
              ["/see-also" :get (conj common-interceptors `git-get)]})

;; Map-based routes
;(def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
;                   :get home-page
;                   "/about" {:get about-page}}})

;; Terse/Vector-based routes
;(def routes
;  `[[["/" {:get home-page}
;      ^:interceptors [(body-params/body-params) http/html-body]
;      ["/about" {:get about-page}]]]])


;; Consumed by agile-savannah-81249.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Tune the Secure Headers
              ;; and specifically the Content Security Policy appropriate to your service/application
              ;; For more information, see: https://content-security-policy.com/
              ;;   See also: https://github.com/pedestal/pedestal/issues/499
              ;;::http/secure-headers {:content-security-policy-settings {:object-src "'none'"
              ;;                                                          :script-src "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"
              ;;                                                          :frame-ancestors "'none'"}}

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ;;  This can also be your own chain provider/server-fn -- http://pedestal.io/reference/architecture-overview#_chain_provider
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port (Integer. (or (System/getenv "PORT") "8080"))
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false
                                        ;; Alternatively, You can specify you're own Jetty HTTPConfiguration
                                        ;; via the `:io.pedestal.http.jetty/http-configuration` container option.
                                        ;:io.pedestal.http.jetty/http-configuration (org.eclipse.jetty.server.HttpConfiguration.)
                                        }})
