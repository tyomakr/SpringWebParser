package ru.aikr.inet.parser.mlfeedback.service;

import org.springframework.stereotype.Service;
import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackRecord;
import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackRequestItem;
import ru.aikr.inet.parser.mlfeedback.repository.MlFeedbackRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class MlFeedbackService {

    private final MlFeedbackRepository repository;

    public MlFeedbackService(MlFeedbackRepository repository) {
        this.repository = repository;
    }

    public int saveFeedback(List<MlFeedbackRequestItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        List<MlFeedbackRecord> records = items.stream()
                .map(item -> {
                    MlFeedbackRecord record = new MlFeedbackRecord(
                            item.candidateId(),
                            item.url(),
                            item.hash(),
                            item.decision() != null ? item.decision().toUpperCase(Locale.ROOT) : null,
                            item.score(),
                            item.reason(),
                            item.zone()
                    );
                    return record;
                })
                .collect(Collectors.toList());
        return repository.saveAll(records);
    }

    public List<MlFeedbackRecord> fetchFeedback(int limit, int offset, Instant since) {
        int safeLimit = Math.max(limit, 1);
        int safeOffset = Math.max(offset, 0);
        return repository.findFeedback(safeLimit, safeOffset, since);
    }
}
