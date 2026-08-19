package ru.tyomakr.akcp.core.content;

import java.util.List;

public interface AnalysisWorkSource {
  /** Returns one bounded batch. A real cursor/progress contract is a separate task. */
  List<AnalysisWorkItem> next(int limit);
}
