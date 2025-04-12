package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.persistence.Index;

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
}
