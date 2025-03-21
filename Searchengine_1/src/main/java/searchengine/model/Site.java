package searchengine.model;

import lombok.*;
import org.hibernate.annotations.Cascade;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "lemmas")
@EqualsAndHashCode(exclude = "lemmas")
@Entity
public class Site {
    public Site(String url, String name, Status status, LocalDateTime statusTime, String lastError) {
        this.url = url;
        this.name = name;
        this.status = status;
        this.statusTime = statusTime;
        this.lastError = lastError;
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(columnDefinition = "VARCHAR(255)")
    private String url;

    @Column(columnDefinition = "VARCHAR(255)")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')")
    private Status status;

    @Column(columnDefinition = "DATETIME")
    private LocalDateTime statusTime;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @OneToMany(mappedBy = "site", orphanRemoval = true)
    @Cascade({}) // Убираем CascadeType.ALL
    private Set<Page> pages = new HashSet<>();


}