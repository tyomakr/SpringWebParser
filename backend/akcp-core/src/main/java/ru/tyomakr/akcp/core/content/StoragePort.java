package ru.tyomakr.akcp.core.content;

import java.io.IOException;
import java.io.InputStream;

public interface StoragePort {
  /**
   * Stores all bytes read from {@code content}. The caller retains ownership of the stream.
   */
  StoredMedia store(InputStream content) throws IOException;

  /**
   * Opens a new stream for the stored object. The caller must close the returned stream.
   */
  InputStream open(StorageReference reference) throws IOException;

  boolean exists(StorageReference reference) throws IOException;
}
