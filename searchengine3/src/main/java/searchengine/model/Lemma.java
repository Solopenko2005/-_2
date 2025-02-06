package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Setter
@Getter
@Entity
@Table(name = "lemma", uniqueConstraints = @UniqueConstraint(columnNames = {"lemma", "site_id"}))
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site; // Связь с сайтом


    @Column(nullable = false, length = 255)
    private String lemma; // Лемма (нормальная форма слова)

    @Column(nullable = false)
    private int frequency; // Количество страниц, на которых встречается лемма

}
