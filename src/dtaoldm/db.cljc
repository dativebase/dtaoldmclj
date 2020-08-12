(ns dtaoldm.db)


(comment

  (->> [1 2 3 4 5]
       (filter odd?))

  (->> [1 2 3 4 5]
       (remove odd?))

  (->> [{:a 2} {:b 22} {:a "dog"}]
       (keep :a))

)
