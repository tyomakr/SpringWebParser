package ru.tyomakr.akcp.core.content;

public interface MediaAnalysisProvider {
  AnalysisProviderDescriptor descriptor();

  MediaAnalysisResult analyze(MediaAnalysisInput input, byte[] encodedImage);
}
