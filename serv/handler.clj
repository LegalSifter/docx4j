(ns serv.handler
  (:require [compojure.core :refer [defroutes POST GET DELETE]]
            [compojure.route :as route]
            [me.raynes.fs :as fs]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]])
  (:import [org.docx4j Docx4J TraversalUtil XmlUtils]
           org.docx4j.anon.Anonymize
           org.docx4j.finders.CommentFinder
           org.docx4j.jaxb.Context
           [org.docx4j.openpackaging.parts.WordprocessingML CommentsExtendedPart CommentsPart]))

(defn docx->wml-package
  [docx-file]
  (Docx4J/load (fs/file docx-file)))

(def file-store (atom {}))

(defn get-xml
  [docx]
  (-> docx docx->wml-package .getMainDocumentPart .getXML))

(defn comment->hash-map
  [c]
  {:contents (map str (.getContent c))
   :author (.getAuthor c)
   :id (.getId c)})

(defn get-comments
  [f]
  (some-> (docx->wml-package f)
          .getParts
          .getParts
          (.get (.getPartName (CommentsPart.)))
          .getContents
          .getComment))

(defn- remove-element
  [parent-content c]
  (let [elem (->> parent-content (filter #(= c (XmlUtils/unwrap %))) first)]
    (.remove parent-content elem)))

(defn- remove-all-comments
  [docx]
  (let [wml (docx->wml-package docx)
        body (-> wml .getMainDocumentPart .getJaxbElement .getBody)
        cf (CommentFinder.)
        _ (TraversalUtil. body cf)
        comments (.getCommentElements cf)]

    (doseq [comment comments]
      (remove-element (-> comment .getParent .getContent) comment))
    (.save wml docx)
    (str "deleted " (count comments) " comment parts")))

(defroutes app-routes
  (GET "/documents" [] (str @file-store))

  (GET "/document/:file-name" [file-name]
    (when-let [docx (get @file-store file-name)]
      {:status 200
       :headers
       {"Content-Type" "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "Content-Disposition" (str "attachment; filename=\"" file-name "\"")}
       :body docx}))

  (GET "/xml/:file-name" [file-name]
    (or (some->> file-name (get @file-store) get-xml)
        (str "docx " file-name " not uploaded")))

  (GET "/comments/:file-name" [file-name]
    (or (some->> file-name (get @file-store) get-comments (map comment->hash-map))
        (str "docx " file-name " not uploaded")))

  ;; curl -X DELETE localhost:3000/document/infile.docx/comments
  (DELETE "/document/:file-name/comments" [file-name]
    (if-let [docx (get @file-store file-name)]
      (remove-all-comments docx)
      (str "docx " file-name " not uploaded")))

  ;; curl -X POST -F infile=@infile.docx -F dog=man localhost:3000/upload
  (POST "/upload" request
    (let [{filename :filename docx :tempfile} (-> request :multipart-params (get "infile"))]
      (swap! file-store assoc filename docx)
      "uploaded"))

  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes (assoc-in site-defaults [:security :anti-forgery] false)))
