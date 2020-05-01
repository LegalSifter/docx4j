(ns serv.handler-test
  (:require [clojure.test :refer :all]
            [serv.handler :as docx]
            [me.raynes.fs :as fs]))

(def ^:private tenant-id "foop")
(def pde-doc-ids (atom #{}))



(def docx-test-dir "test/resources/docx-test")


;; build with
;; mvn clean install -DskipTests

;; run with
;; docker run -it --rm -p 3000:3000 -v $(readlink -f serv):/usr/src/app --entrypoint=bash docx4j-serv   lein test

(deftest this-fails
  (doseq [[docx-file run-first-test? id]

          [

           ;; tests pass
           [(str docx-test-dir "/target-dne.zip") false :1]

           ;; ;; this fails for me with 10 broken cant be fixed wtf
           [(str docx-test-dir "/target-dne.zip") true :2]

           ;; causes JVM to crash, but ONLY if test :1 and/or :2 are run
           ;; otherwise this test passes
           [(str docx-test-dir "/target-null.zip") false :3]

           ;; this causes JVM to crash absolutely.  no other tests need to be run
           ;; so, invoking find-broken-relationships on sanity.docx, then
           ;;  copying then modifying the file in find-and-remove-broken-relationships
           ;;  returning the copied file then running find-broken-relationships on that copied one
           ;;  causes the JVM to crash
           [(str docx-test-dir "/target-null.zip") true :4]

           ]]
    

    (when run-first-test?
      (testing (str "testing sanity ... id: " id)
        (println "\n\nend2end id:" id)
        (is (empty? (#'docx/find-broken-relationships (str docx-test-dir "/sanity.docx"))))))

    (testing (str "find and remove broken rels ... id: " id)
      (println "\n\nfind-broken-relationships id:" id)
      (let [modded-docx (docx/find-and-remove-broken-relationships docx-file)
            _ (fs/copy modded-docx (fs/file "modded2.docx"))
            broken-hearts (docx/find-broken-relationships modded-docx)]

        (is (empty? broken-hearts)
            (str "found " (count broken-hearts) " broken relationships that dont couldn't be mended: " broken-hearts))))))


