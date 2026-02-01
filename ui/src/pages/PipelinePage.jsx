import React, { useEffect, useMemo, useRef, useState } from 'react';
import Stepper from '../components/Stepper.jsx';
import { useAuth } from '../store/auth.jsx';
import { useTheme } from '../store/theme.jsx';
import { apiRequest } from '../api/client.js';

const steps = [
  { id: 'ingest', label: 'Ingest' },
  { id: 'library', label: 'Library / Gallery' },
  { id: 'publish', label: 'Publish VK' },
];

export default function PipelinePage() {
  const { username, logout, token } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [activeStep, setActiveStep] = useState(1);
  const [ingestMode, setIngestMode] = useState('fishki');
  const [url, setUrl] = useState('');
  const [pageFrom, setPageFrom] = useState('1');
  const [pageTo, setPageTo] = useState('1');
  const [isParsing, setIsParsing] = useState(false);
  const [parseError, setParseError] = useState('');
  const [attachments, setAttachments] = useState([]);
  const [attachmentsCount, setAttachmentsCount] = useState(0);
  const [createdItemId, setCreatedItemId] = useState(null);
  const [parseMeta, setParseMeta] = useState(null);
  const [fishkiJobId, setFishkiJobId] = useState(null);
  const [fishkiJobStatus, setFishkiJobStatus] = useState(null);
  const [libraryItem, setLibraryItem] = useState(null);
  const [libraryError, setLibraryError] = useState('');
  const [libraryLoading, setLibraryLoading] = useState(false);
  const [galleryPage, setGalleryPage] = useState(1);
  const [selectedIds, setSelectedIds] = useState([]);
  const [previewMedia, setPreviewMedia] = useState(null);
  const [historyItems, setHistoryItems] = useState([]);
  const [historyCursor, setHistoryCursor] = useState(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const galleryRef = useRef(null);
  const pageSize = 40;

  const attachmentKey = (attachment) => attachment.id || attachment.url;

  const isVideoAttachment = (attachment) => {
    if (!attachment) {
      return false;
    }
    if (attachment.type === 'VIDEO') {
      return true;
    }
    const url = attachment.url || '';
    return url.endsWith('.mp4') || url.endsWith('.webm');
  };

  const handleParse = async () => {
    setParseError('');
    setIsParsing(true);
    setAttachments([]);
    setAttachmentsCount(0);
    setCreatedItemId(null);
    setParseMeta(null);
    setFishkiJobId(null);
    setFishkiJobStatus(null);
    setLibraryItem(null);
    setLibraryError('');
    setGalleryPage(1);
    setSelectedIds([]);
    setPreviewMedia(null);
    try {
      let endpoint = '/api/ingestion/web/parse';
      let body = { url, createItem: true };
      let parsedFrom = null;
      let parsedTo = null;
      if (ingestMode === 'fishki') {
        parsedFrom = Number(pageFrom);
        parsedTo = Number(pageTo);
        if (!Number.isFinite(parsedFrom) || !Number.isFinite(parsedTo)) {
          throw new Error('Page range должен быть числом');
        }
        endpoint = '/api/ingestion/web/fishki/parse-async';
        body = { pageFrom: parsedFrom, pageTo: parsedTo, createItem: true };
      } else if (!url) {
        throw new Error('URL обязателен');
      }
      const result = await apiRequest(endpoint, {
        method: 'POST',
        body,
        token,
      });
      if (ingestMode === 'fishki') {
        setParseMeta({
          pageFrom: parsedFrom,
          pageTo: parsedTo,
          pagesParsed: Math.max(1, parsedTo - parsedFrom + 1),
          jobId: result.jobId,
          status: result.status,
        });
        setFishkiJobId(result.jobId);
        setFishkiJobStatus(result.status);
      } else {
        setAttachments(result.attachments || []);
        setAttachmentsCount((result.attachments || []).length);
        setCreatedItemId(result.createdItemId || null);
        setParseMeta({
          title: result.title,
          url: result.url,
        });
      }
      if (token) {
        void loadHistory(true);
      }
    } catch (error) {
      setParseError(error.message || 'Ошибка парсинга');
    } finally {
      setIsParsing(false);
    }
  };

  useEffect(() => {
    if (activeStep !== 2) {
      return;
    }
    if (!createdItemId) {
      setLibraryError('Сначала выполните парсинг и создайте Item.');
      return;
    }
    let isCancelled = false;
    const load = async () => {
      setLibraryLoading(true);
      setLibraryError('');
      try {
        const item = await apiRequest(`/api/items/${createdItemId}`, { token });
        if (!isCancelled) {
          setLibraryItem(item);
        }
      } catch (error) {
        if (!isCancelled) {
          setLibraryError(error.message || 'Не удалось загрузить Item');
        }
      } finally {
        if (!isCancelled) {
          setLibraryLoading(false);
        }
      }
    };
    load();
    return () => {
      isCancelled = true;
    };
  }, [activeStep, createdItemId, token]);

  useEffect(() => {
    if (activeStep !== 2) {
      return;
    }
    if (!galleryRef.current) {
      return;
    }
    galleryRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [activeStep, galleryPage]);

  useEffect(() => {
    if (!fishkiJobId || !token) {
      return;
    }
    let isCancelled = false;
    const poll = async () => {
      try {
        const status = await apiRequest(`/api/ingestion/web/fishki/jobs/${fishkiJobId}`, { token });
        if (isCancelled) {
          return;
        }
        setFishkiJobStatus(status.status);
        setParseMeta((prev) => (prev ? { ...prev, status: status.status } : prev));
        if (status.status === 'DONE') {
          setAttachmentsCount(status.attachmentsCount || 0);
          if (status.createdItemId) {
            setCreatedItemId(status.createdItemId);
          }
          setFishkiJobId(null);
          void loadHistory(true);
        }
        if (status.status === 'FAILED') {
          setParseError(status.lastError || 'Задача завершилась с ошибкой');
          setFishkiJobId(null);
        }
      } catch (error) {
        if (!isCancelled) {
          setParseError(error.message || 'Не удалось загрузить статус задачи');
        }
      }
    };
    const interval = setInterval(poll, 2000);
    void poll();
    return () => {
      isCancelled = true;
      clearInterval(interval);
    };
  }, [fishkiJobId, token]);

  useEffect(() => {
    if (activeStep !== 1 || !token) {
      return;
    }
    void loadHistory(true);
  }, [activeStep, token]);

  const loadHistory = async (reset = false) => {
    setHistoryLoading(true);
    setHistoryError('');
    try {
      const cursorParam = reset ? null : historyCursor;
      const query = cursorParam ? `?limit=20&cursor=${encodeURIComponent(cursorParam)}` : '?limit=20';
      const response = await apiRequest(`/api/items${query}`, { token });
      setHistoryItems((prev) => (reset ? response.items : [...prev, ...response.items]));
      setHistoryCursor(response.nextCursor || null);
    } catch (error) {
      setHistoryError(error.message || 'Не удалось загрузить историю');
    } finally {
      setHistoryLoading(false);
    }
  };

  const openHistoryItem = (item) => {
    setCreatedItemId(item.id);
    setLibraryItem(item);
    setLibraryError('');
    setGalleryPage(1);
    setSelectedIds([]);
    setPreviewMedia(null);
    setActiveStep(2);
  };

  const toggleSelected = (attachment) => {
    const key = attachmentKey(attachment);
    setSelectedIds((prev) => (
      prev.includes(key)
        ? prev.filter((id) => id !== key)
        : [...prev, key]
    ));
  };

  const galleryState = useMemo(() => {
    const list = libraryItem?.attachments || [];
    const total = list.length;
    const totalPages = total === 0 ? 1 : Math.ceil(total / pageSize);
    const page = Math.min(galleryPage, totalPages);
    const start = (page - 1) * pageSize;
    const slice = list.slice(start, start + pageSize);
    return { total, totalPages, page, slice };
  }, [libraryItem, galleryPage]);

  const selectedAttachments = useMemo(() => {
    const list = libraryItem?.attachments || [];
    if (selectedIds.length === 0) {
      return [];
    }
    return list.filter((attachment) => selectedIds.includes(attachmentKey(attachment)));
  }, [libraryItem, selectedIds]);

  let stepContent;
  if (activeStep === 1) {
    stepContent = (
      <div className="panel">
        <h2>Шаг 1 · Ingest URL</h2>
        <p className="muted">Выберите режим парсинга и запустите сбор изображений.</p>
        <div className="ingest-tabs">
          <button
            type="button"
            className={`tab ${ingestMode === 'url' ? 'active' : ''}`}
            onClick={() => setIngestMode('url')}
          >
            URL
          </button>
          <button
            type="button"
            className={`tab ${ingestMode === 'fishki' ? 'active' : ''}`}
            onClick={() => setIngestMode('fishki')}
          >
            Fishki (pages)
          </button>
        </div>
        <div className="form-grid">
          {ingestMode === 'url' ? (
            <label>
              URL страницы
              <input
                type="url"
                placeholder="https://example.com/page"
                value={url}
                onChange={(event) => setUrl(event.target.value)}
              />
            </label>
          ) : (
            <div className="field-row">
              <label>
                Page from
                <input
                  type="number"
                  min="1"
                  value={pageFrom}
                  onChange={(event) => setPageFrom(event.target.value)}
                />
              </label>
              <label>
                Page to
                <input
                  type="number"
                  min="1"
                  value={pageTo}
                  onChange={(event) => setPageTo(event.target.value)}
                />
              </label>
            </div>
          )}
          <button type="button" onClick={handleParse} disabled={isParsing}>
            {isParsing ? 'Парсим...' : 'Parse'}
          </button>
          {parseError ? <p className="error">{parseError}</p> : null}
        </div>

        {parseMeta ? (
          <div className="meta">
            {ingestMode === 'fishki' ? (
              <p className="muted">
                Fishki: {parseMeta.pageFrom}–{parseMeta.pageTo}, страниц: {parseMeta.pagesParsed}
              </p>
            ) : (
              <p className="muted">{parseMeta.title || parseMeta.url}</p>
            )}
            {parseMeta.jobId ? <p className="muted">Job: {parseMeta.jobId}</p> : null}
            <p>
              Item: {createdItemId || '—'} · Найдено изображений: {attachmentsCount}
            </p>
            {parseMeta.status ? (
              <p className="muted">Статус задачи: {parseMeta.status}</p>
            ) : null}
          </div>
        ) : null}
        <div className="history">
          <div className="history-header">
            <h3>История запросов</h3>
            <div className="history-actions">
              <button
                type="button"
                className="ghost"
                onClick={() => loadHistory(true)}
                disabled={historyLoading}
              >
                Обновить
              </button>
              <span className="muted">всего {historyItems.length}</span>
            </div>
          </div>
          {historyError ? <p className="error">{historyError}</p> : null}
          {historyLoading && historyItems.length === 0 ? (
            <div className="empty">Загрузка...</div>
          ) : null}
          {!historyLoading && historyItems.length === 0 ? (
            <div className="empty">История пуста</div>
          ) : null}
          {historyItems.length > 0 ? (
            <>
              <div className="history-table">
                <div className="history-row history-head">
                  <span>Дата/время</span>
                  <span>Источник</span>
                  <span>Заголовок / URL</span>
                  <span>Найдено</span>
                  <span>Действие</span>
                </div>
                {historyItems.map((item) => (
                  <div key={item.id} className="history-row">
                    <span>{new Date(item.createdAt).toLocaleString()}</span>
                    <span>{item.sourceType}</span>
                    <span>{item.title || item.sourceUrl || '—'}</span>
                    <span>{item.attachments?.length || 0}</span>
                    <span>
                      <button type="button" className="ghost" onClick={() => openHistoryItem(item)}>
                        Открыть
                      </button>
                    </span>
                  </div>
                ))}
              </div>
              {historyCursor ? (
                <button
                  type="button"
                  className="ghost"
                  onClick={() => loadHistory(false)}
                  disabled={historyLoading}
                >
                  Показать ещё
                </button>
              ) : null}
            </>
          ) : null}
        </div>
        <div className="step-actions">
          <button
            type="button"
            className="ghost"
            disabled={!createdItemId}
            onClick={() => setActiveStep(2)}
          >
            К галерее
          </button>
        </div>
      </div>
    );
  } else if (activeStep === 2) {
    stepContent = (
      <div className="panel">
        <h2>Шаг 2 · Library / Gallery</h2>
        <p className="muted">Галерея изображений из созданного Item.</p>
        {libraryLoading ? <p className="muted">Загрузка...</p> : null}
        {libraryError ? <p className="error">{libraryError}</p> : null}
        {libraryItem ? (
          <>
            <div className="meta">
              <p className="muted">Item: {libraryItem.id}</p>
              <p className="muted">Всего: {galleryState.total}</p>
              <p className="muted">Отобрано: {selectedIds.length}</p>
            </div>
            <div ref={galleryRef} className="gallery">
              {galleryState.slice.length === 0 ? (
                <div className="empty">В этом Item нет вложений</div>
              ) : (
                galleryState.slice.map((attachment) => {
                  const key = attachmentKey(attachment);
                  const isSelected = selectedIds.includes(key);
                  return (
                    <div key={key} className={`thumb-card ${isSelected ? 'selected' : ''}`}>
                      {isVideoAttachment(attachment) ? (
                        <video src={attachment.url} muted playsInline />
                      ) : (
                        <img src={attachment.url} alt="" loading="lazy" />
                      )}
                      <div className="thumb-overlay">
                        <button
                          type="button"
                          className="thumb-action"
                          onClick={() => toggleSelected(attachment)}
                        >
                          {isSelected ? 'Убрать' : 'В отбор'}
                        </button>
                        <button
                          type="button"
                          className="thumb-action ghost-invert"
                          onClick={() => setPreviewMedia({ url: attachment.url, type: attachment.type })}
                        >
                          Просмотр
                        </button>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
            <div className="pager">
              <button
                type="button"
                className="ghost"
                disabled={galleryState.page <= 1}
                onClick={() => setGalleryPage((prev) => Math.max(1, prev - 1))}
              >
                Назад
              </button>
              <span className="muted">
                Страница {galleryState.page} / {galleryState.totalPages}
              </span>
              <button
                type="button"
                className="ghost"
                disabled={galleryState.page >= galleryState.totalPages}
                onClick={() => setGalleryPage((prev) => Math.min(galleryState.totalPages, prev + 1))}
              >
                Вперёд
              </button>
            </div>
            {previewMedia ? (
              <div className="lightbox" onClick={() => setPreviewMedia(null)}>
                <div className="lightbox-inner" onClick={(event) => event.stopPropagation()}>
                  {isVideoAttachment(previewMedia) ? (
                    <video src={previewMedia.url} controls />
                  ) : (
                    <img src={previewMedia.url} alt="" />
                  )}
                  <button type="button" className="ghost" onClick={() => setPreviewMedia(null)}>
                    Закрыть
                  </button>
                </div>
              </div>
            ) : null}
            <div className="step-actions">
              <button type="button" className="ghost" onClick={() => setActiveStep(1)}>
                Назад
              </button>
              <button
                type="button"
                className="ghost"
                onClick={() => setActiveStep(3)}
                disabled={selectedIds.length === 0}
              >
                К публикации
              </button>
            </div>
          </>
        ) : null}
      </div>
    );
  } else {
    stepContent = (
      <div className="panel">
        <h2>Шаг 3 · Publish VK</h2>
        <p className="muted">Проверьте выбранные изображения и отправьте публикацию в очередь.</p>
        {!libraryItem ? (
          <div className="empty">Нет данных. Сначала выполните парсинг и отбор.</div>
        ) : (
          <>
            <div className="meta">
              <p className="muted">Item: {libraryItem.id}</p>
              <p className="muted">К публикации: {selectedAttachments.length}</p>
            </div>
            <div className="gallery">
              {selectedAttachments.length === 0 ? (
                <div className="empty">Ничего не отобрано</div>
              ) : (
                selectedAttachments.map((attachment) => {
                  const key = attachmentKey(attachment);
                  return (
                    <div key={key} className="thumb-card selected">
                      {isVideoAttachment(attachment) ? (
                        <video src={attachment.url} muted playsInline />
                      ) : (
                        <img src={attachment.url} alt="" loading="lazy" />
                      )}
                      <div className="thumb-overlay">
                        <button
                          type="button"
                          className="thumb-action"
                          onClick={() => toggleSelected(attachment)}
                        >
                          Удалить
                        </button>
                        <button
                          type="button"
                          className="thumb-action ghost-invert"
                          onClick={() => setPreviewMedia({ url: attachment.url, type: attachment.type })}
                        >
                          Просмотр
                        </button>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
            {previewMedia ? (
              <div className="lightbox" onClick={() => setPreviewMedia(null)}>
                <div className="lightbox-inner" onClick={(event) => event.stopPropagation()}>
                  {isVideoAttachment(previewMedia) ? (
                    <video src={previewMedia.url} controls />
                  ) : (
                    <img src={previewMedia.url} alt="" />
                  )}
                  <button type="button" className="ghost" onClick={() => setPreviewMedia(null)}>
                    Закрыть
                  </button>
                </div>
              </div>
            ) : null}
            <div className="step-actions">
              <button type="button" className="ghost" onClick={() => setActiveStep(2)}>
                Назад к галерее
              </button>
            </div>
          </>
        )}
      </div>
    );
  }

  return (
    <main className="pipeline">
      <header className="topbar">
        <div>
          <p className="eyebrow">AK Content Pipeline</p>
          <h1>Manual workflow</h1>
        </div>
        <div className="user-box">
          <button type="button" onClick={toggleTheme} className="ghost">
            Тема: {theme === 'dark' ? 'тёмная' : 'светлая'}
          </button>
          <span>{username || 'user'}</span>
          <button type="button" onClick={logout} className="ghost">Выйти</button>
        </div>
      </header>

      <section className="stepper-card">
        <Stepper steps={steps} activeStep={activeStep} onStepChange={setActiveStep} />
        {stepContent}
      </section>
    </main>
  );
}
