package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.persistence.Index;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "page", indexes = {
        @Index(name = "idx_path", columnList = "path")
})
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false, length = 255)
    private String path;

    @Column(nullable = false)
    private Integer code;  // HTTP-код страницы

    @Column(nullable = false, columnDefinition = "VARCHAR")
    private String content;

    public Page(Site site, String replace, int i, String s) {
    }

    public Page() {
    }

    public void setUrl(String url) {
    }
    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Topic> topics = new ArrayList<>();

    @Column(name = "topic_count")
    private Integer topicCount = 0;

    // Добавьте метод для добавления темы
    public void addTopic(Topic topic) {
        topics.add(topic);
        topic.setPage(this);
        topic.setSite(this.getSite());
        topicCount = topics.size();
    }
}
