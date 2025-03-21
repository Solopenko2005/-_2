package searchengine.model;

import lombok.*;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    @OneToMany(mappedBy = "lemma", orphanRemoval = true)
    @Cascade({}) // Убираем CascadeType.ALL
    private Set<SearchIndex> searchIndexes = new HashSet<>();

    public Lemma(String lemma, Site site, int frequency) {
        this.lemma = lemma;
        this.site = site;
        this.frequency = frequency;
    }
}
