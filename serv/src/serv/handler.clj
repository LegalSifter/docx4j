(ns serv.handler
  (:require [compojure.core :refer [defroutes POST GET DELETE]]
            [compojure.route :as route]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [throw+ try+]]
            [clojure.string :as s]
            [compojure.core :refer [defroutes POST GET DELETE]]
            [compojure.route :as route]
            [me.raynes.fs :as fs]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [me.raynes.fs :as fs]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]])
  (:import [org.docx4j Docx4J TraversalUtil XmlUtils]
           java.util.zip.ZipFile
           org.docx4j.anon.Anonymize
           org.docx4j.finders.CommentFinder
           org.docx4j.jaxb.Context
           [org.docx4j.openpackaging.parts.WordprocessingML CommentsExtendedPart CommentsPart]))

(defn ->big-int
  "attempts to coerce arg to BigInteger.
   nil if arg is nil.
   else does whatever java.math.BigInteger/valueOf does"
  [x]
  (condp = (type x)
    java.lang.String (-> x read-string ->big-int)
    java.math.BigInteger x
    nil nil
    (java.math.BigInteger/valueOf x)))

(def docx->wml-package
  (memoize
   (fn [docx-file]
     (Docx4J/load (fs/file docx-file)))))

(defn save
  "TODO: catch save error here and throw with user-facing error"
  [wml dest-file]
  (.save wml (fs/file dest-file)))

(defn anonymize
  "creates and returns an anonymous version of infile"
  ([infile]
   (anonymize infile (fs/temp-file "")))

  ([infile outfile]
   (let [wml (docx->wml-package infile)
         anon (Anonymize. wml)
         result (.go anon)]

     (save wml outfile)

     (when-not (.isOK result)
       
       (when (pos? (.. result getUnsafeParts size))
         ))

     outfile)))

(defn- create-comment-ref
  [id]
  (let [obj-factory (Context/getWmlObjectFactory)
        run (.createR obj-factory)
        reference (.createRCommentReference obj-factory)]
    (.add (.getContent run) reference)
    (.setId reference id)
    run))

(defn- remove-element
  [parent-content c]
  (let [elem (->> parent-content
                  (filter #(= c (XmlUtils/unwrap %)))
                  first)]
    (.remove parent-content elem)))

(defn- replace-element
  [parent-content match replacement]
  (doseq [[idx elem] (map-indexed vector parent-content)]
    (when (= (XmlUtils/unwrap elem) match)
      (.set parent-content idx replacement)
      (remove-element parent-content elem))))

(defn get-comments
  [docx-file]
  (some-> docx-file
          docx->wml-package
          .getParts
          .getParts
          (.get (.getPartName (CommentsPart.)))
          .getContents
          ;; Not a typo.  This does return a collection
          .getComment))

(defn- comment-id->paraids
  [docx-file comment-id]
  (->> (get-comments (str docx-file))
       (filter #(= (->big-int comment-id) (.getId %)))
       (map (fn [c]
              (some->> c .getContent (map #(.getParaId %)) set)))
       first))

(defn comment-has-reply?
  [docx-file comment-id]
  (let [paraids (comment-id->paraids docx-file comment-id)
        extended-parts (some-> (docx->wml-package docx-file)
                               .getParts
                               .getParts
                               (.get (.getPartName (CommentsExtendedPart.)))
                               .getContents
                               .getCommentEx)]
    (->> extended-parts
         (filter #(contains? paraids (.getParaIdParent %)))
         seq)))

(defn update-comments!
  "mutates the docx-file by applying the fns in update-map to their corresponding comment.
   supports setAuthor, setInitials, setDate.

   update-map looks something like {:comment-id (fn [comment] (.setAuthor % author-name))}."
  [orig-docx-file update-map]

  (try+
    (let [docx-file (fs/copy orig-docx-file (fs/temp-file "" ".docx"))
          wml (docx->wml-package docx-file)
          comments (some-> wml
                           .getParts
                           .getParts
                           (.get (.getPartName (CommentsPart.)))
                           .getContents
                           .getComment)]

      ;; apply all the fns
      (doseq [c comments]
        (doseq [f (get update-map (.getId c))]
          (f c)))

      (save wml docx-file)
      (fs/copy docx-file orig-docx-file)
      (fs/delete docx-file))

    (catch Object e
      )))

(defn- get-parent-paragraph-content
  [elem]
  (when elem
    (if-not (instance? org.docx4j.wml.P elem)
      (get-parent-paragraph-content (.getParent elem))
      (.getContent elem))))

(defn set-comment-lengths-to-zero!
  "mutates the docx-file by setting the length of comments to zero"
  [orig-docx-file comment-ids]

  (try+
    (let [docx-file (fs/copy orig-docx-file (fs/temp-file "" ".docx"))

          wml (docx->wml-package docx-file)
          body (-> wml .getMainDocumentPart .getJaxbElement .getBody)
          cf (CommentFinder.)
          _ (TraversalUtil. body cf)
          elements-by-id (group-by #(.getId %) (.getCommentElements cf))]

      (doseq [[comment-id comment-elements] elements-by-id]
        (when (contains? (set comment-ids) comment-id)
          (let [get-comment (fn [clazz]
                              (->> comment-elements (filter (partial instance? clazz)) first))
                comment-range-start (get-comment org.docx4j.wml.CommentRangeStart)
                comment-range-end (get-comment org.docx4j.wml.CommentRangeEnd)
                comment-ref (get-comment org.docx4j.wml.R$CommentReference)]

            (when comment-range-start
              (let [[start-pc end-pc ref-pc] (->> [comment-range-start comment-range-end comment-ref]
                                                  (map get-parent-paragraph-content))]
                (replace-element start-pc comment-range-start (create-comment-ref comment-id))
                (remove-element end-pc comment-range-end)
                (remove-element ref-pc (.getParent comment-ref)))))))

      (save wml docx-file)
      (fs/copy docx-file orig-docx-file)
      (fs/delete docx-file))

    ;; TODO: move this inside the `doseq` since this is just a warning
    ;; and it'd make sense to continue working on the remaining comments
    (catch Object e
      
      )))

(defn- get-deltext-tags
  [docx-file]
  (let [;; false b/c getting errors when true, and the content is static
        ;; TODO: does this suggest an underlying issue?
        refresh-xml-first? false]
    (-> docx-file
        docx->wml-package
        .getMainDocumentPart
        (.getJAXBNodesViaXPath ".//w:delText" refresh-xml-first?))))

(defn replace-redline-deletions-with-chars!
  "Replaces each char in deltext each tag with a single space.
  Resulting raw text length should not change"
  ([infile]
   (replace-redline-deletions-with-chars! infile (fs/temp-file "" ".docx")))

  ([infile outfile]
   (let [replace-del-text-tag-with-whitespace! (fn [deltext-tag]
                                                 (let [length (some-> deltext-tag .getValue count)
                                                       replacement-str (s/join (repeat length " "))]
                                                   ;; .setSpace has no effect while the B-92 hack is in place.
                                                   ;; but it will be necessary after that
                                                   (.setSpace deltext-tag "preserve")
                                                   (.setValue deltext-tag replacement-str)))]
     (doseq [deltext-tag (get-deltext-tags infile)]
       (try+
         (replace-del-text-tag-with-whitespace! deltext-tag)
         ;; (catch Object e
         ;;   (let [error-code (errors/scenario->error-code [:docx :redline-deletion])
         ;;         payload {:source (aws/task-bucket)
         ;;                  :attachments [{:text (slack/format-text-attachment {:xargs {:body (errors/error->body e)
         ;;                                                                              :deltext-tag deltext-tag}})

         ;;                                 :color "error"}]
         ;;                  :message (str "`" error-code "`: Failed replacing the contents of a delText tag for `" infile "`")}]
         ;;     (slack/send-pde-exception payload)))
         )))

   (save (docx->wml-package infile) outfile)
   outfile))

(defn docx4j-relationship-id?
  "Digging into the Docx4j code, JH states that a relationship ID is
  only valid if it's of the form `rID1234343`.  So, we can infer that
  if that pattern isn't followed here, it must not be a relationship
  managed/created by Docx4j.
  See org.docx4j.openpackaging.parts.relationships/resetIdAllocator

  Prolly should not be committing this as it's so totally super tied
  to implementation details, BUT this is all part of the great HACK
  so Accusoft can't be bothered with bugs we can work around"
  [id]
  (re-find #"^\d+$" (subs id 3)))

(defn- get-document-relationships
  "Returns a list of org.docx4j.relationships.Relationship.
  Represents what's in _rels/document.xml.rels.
  Ignores targets that exist outside of the 'word' directory"
  [docx-file]
  (some->> docx-file
           docx->wml-package
           .getMainDocumentPart
           .getRelationshipsPart
           .getJaxbElement
           .getRelationship))

(defn find-broken-relationships
  [docx-file]
  (let [;; docx-file (fs/copy docx-file (fs/temp-file ""))
        ;; the JVM will crash under certain circumstances if a docx-file
        ;; is being modified while being iterated on.
        ;; To work around this, a clone of the document is created
        _ (println (.toString docx-file))
        docx-xml-filepaths (->> (ZipFile. docx-file)
                                .entries
                                iterator-seq
                                (map #(-> % str fs/file .getCanonicalPath))
                                set
                                )
        ]
    (->> (get-document-relationships docx-file)
         (remove #(contains? docx-xml-filepaths (->> % .getTarget (str "word/") fs/file .getCanonicalPath)))
         set)
    ))

(defn find-and-remove-broken-relationships
  [orig-docx-file]
  (def orig-docx-file orig-docx-file)
  (try+
    (let [docx-file (fs/copy orig-docx-file (fs/temp-file ""))
          wml (docx->wml-package docx-file)
          all (get-document-relationships docx-file)
          failed-removals (atom [])
          broken-relationships (find-broken-relationships docx-file)]
      (println (str (count broken-relationships) " broken-relationships out of " (count all) " rels"))

      (def broken-relationships broken-relationships)
      (doseq [rel broken-relationships]
        (if (.remove all rel)
          
          (swap! failed-removals conj rel)))

      (save wml docx-file)

      (when (seq @failed-removals)
        ;; FIXME: throw so whomever has LS knowledge can handle it
        )

      ;; (let [test-file (fs/temp-file "broken-rel-test")
      ;;       test-file docx-file]
      ;;   ;; (fs/copy docx-file test-file)
      ;;   (println (cli=> (str "md5sum " test-file)))
      ;;   (println (cli=> (str "md5sum " docx-file)))
      ;;   (let [wml2 (docx->wml-package test-file)]
      ;;     (when-let [still-broken (seq (find-broken-relationships wml2 test-file))]
      ;;       (throw+  "couldnt fix broken relationships!!!  " (count still-broken) " remaining out of " (count (get-document-relationships wml2)) " total")))
      ;;   (def still-broken still-broken))
      
      docx-file
      ;; (fs/copy docx-file orig-docx-file)
      ;; (fs/delete docx-file)
      )
    (catch Object e
      (throw+)
      ;; (throw+)
      )))


;; (def file-store (atom {}))
(defroutes app-routes
  ;; (GET "/documents" [] (str @file-store))

  ;; (GET "/document/:file-name" [file-name]
  ;;   (when-let [docx (get @file-store file-name)]
  ;;     {:status 200
  ;;      :headers
  ;;      {"Content-Type" "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
  ;;       "Content-Disposition" (str "attachment; filename=\"" file-name "\"")}
  ;;      :body docx}))

  ;; (GET "/xml/:file-name" [file-name]
  ;;   (or (some->> file-name (get @file-store) get-xml)
  ;;       (str "docx " file-name " not uploaded")))

  ;; (GET "/comments/:file-name" [file-name]
  ;;   (or (some->> file-name (get @file-store) get-comments (map comment->hash-map))
  ;;       (str "docx " file-name " not uploaded")))

  ;; ;; curl -X DELETE localhost:3000/document/infile.docx/comments
  ;; (DELETE "/document/:file-name/comments" [file-name]
  ;;   (if-let [docx (get @file-store file-name)]
  ;;     (remove-all-comments docx)
  ;;     (str "docx " file-name " not uploaded")))

  (GET "/" _ (println "dogman") "OKaaaaa"
    )

  (POST "/find-broken-relationships" request
    (let [{file-name :filename docx :tempfile} (-> request :multipart-params (get "infile"))]
      (def file-name file-name)
      (def docx docx)
      (def request request)
      (taoensso.timbre/spy :debug request)
      (->> (find-broken-relationships docx)
           (mapv #(.getId %))
           str)))

  (POST "/find-and-remove-broken-relationships" request
    (let [{file-name :filename docx :tempfile} (-> request :multipart-params (get "infile"))]
      (def file-name file-name)
      (def docx docx)
      (def request request)
      (println "\n\n")
      (println (System/getProperty "java.version"))
      (println "\n\n")
      (taoensso.timbre/spy :debug request)
      {:status 200
       :headers
       {"Content-Type" "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "Content-Disposition" (str "attachment; filename=\"" file-name "\"")}
       :body (find-and-remove-broken-relationships docx)}))

  ;; curl -X POST -F infile=@infile.docx -F dog=man localhost:3000/upload
  (POST "/anonymize" request
    (let [{file-name :filename docx :tempfile} (-> request :multipart-params (get "infile"))]
      (def file-name file-name)
      (def docx docx)
      (def request request)
      (taoensso.timbre/spy :debug request)
      {:status 200
       :headers
       {"Content-Type" "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "Content-Disposition" (str "attachment; filename=\"" file-name "\"")}
       :body (anonymize docx)}))

  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes (assoc-in site-defaults [:security :anti-forgery] false)))
