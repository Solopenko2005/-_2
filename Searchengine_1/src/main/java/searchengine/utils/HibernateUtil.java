package searchengine.utils;

import lombok.Getter;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import searchengine.model.Lemma;

public class HibernateUtil {
    // Метод для получения текущей сессии
    @Getter
    private static SessionFactory sessionFactory;

    static {
        try {
            // Создаем SessionFactory из конфигурационного файла Hibernate
            sessionFactory = new Configuration().configure("hibernate.cfg.xml").addAnnotatedClass(Lemma.class).buildSessionFactory();

        } catch (Exception e) {
            e.printStackTrace();
            throw new ExceptionInInitializerError(e);
        }
    }

    // Закрытие фабрики сессий (в идеале вызовите это при завершении работы приложения)
    public static void shutdown() {
        getSessionFactory().close();
    }

}
