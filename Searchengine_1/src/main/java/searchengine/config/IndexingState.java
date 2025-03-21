package searchengine.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

import static searchengine.services.LemmaService.logger;

@Component
public class IndexingState {
    private final AtomicBoolean indexingInProgress = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public boolean isIndexingInProgress() {
        return indexingInProgress.get();
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public synchronized boolean setIndexingIfAvailable() {
        if (indexingInProgress.compareAndSet(false, true)) {
            stopRequested.set(false);
            return true;
        }
        return false;
    }

    public  void requestStop() {
        stopRequested.set(true);
    }

    public void reset() {
        indexingInProgress.set(false);
        stopRequested.set(false);
    }

    // Методы для управления состоянием остаются нестатическими
    public void setIndexingInProgress(boolean value) {
        indexingInProgress.set(value);
    }
}
