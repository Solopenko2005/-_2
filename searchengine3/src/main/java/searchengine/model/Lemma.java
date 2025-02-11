package searchengine.model;

import lombok.*;

import javax.persistence.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = "site")
@ToString(exclude = "site")
@Entity
@Table(
        name = "lemma",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lemma", "site_id"})
)
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, foreignKey = @ForeignKey(name = "fk_lemma_site"))
    private Site site;

    @Column(nullable = false, length = 255)
    private String lemma;

    @Column(nullable = false)
    private int frequency;
    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SearchIndex> searchIndexes;

    public Lemma(String lemma, Site site, int frequency) {
        this.lemma = lemma;
        this.site = site;
        this.frequency = frequency;
    }
}
