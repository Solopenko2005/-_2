package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.config.IndexingSettings;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.List;

@Service
public class SiteDataService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void clearSiteData(List<IndexingSettings.SiteConfig> sites) {
        for (IndexingSettings.SiteConfig site : sites) {
            entityManager.createQuery("DELETE FROM Page p WHERE p.site.url = :siteUrl")
                    .setParameter("siteUrl", site.getUrl())
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM Site s WHERE s.url = :siteUrl")
                    .setParameter("siteUrl", site.getUrl())
                    .executeUpdate();
        }
    }
}
