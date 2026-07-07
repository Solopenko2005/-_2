package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "topic_group")
@Getter
@Setter
public class TopicGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "content_summary", columnDefinition = "TEXT")
    private String contentSummary;

    @Column(name = "frequency", nullable = false)
    private int frequency = 0;

    @Column(name = "site_count", nullable = false)
    private int siteCount = 0;

    @OneToMany(mappedBy = "topicGroup", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Topic> topics = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "topic_group_key_lemmas", joinColumns = @JoinColumn(name = "topic_group_id"))
    @Column(name = "lemma")
    private Set<String> keyLemmas = new HashSet<>();
}