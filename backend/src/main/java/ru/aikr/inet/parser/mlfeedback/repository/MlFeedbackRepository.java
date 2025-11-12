package ru.aikr.inet.parser.mlfeedback.repository;

import ru.aikr.inet.parser.mlfeedback.model.MlFeedbackRecord;

import java.time.Instant;
import java.util.List;

public interface MlFeedbackRepository {

    int saveAll(List<MlFeedbackRecord> records);

    List<MlFeedbackRecord> findFeedback(int limit, int offset, Instant since);
}
