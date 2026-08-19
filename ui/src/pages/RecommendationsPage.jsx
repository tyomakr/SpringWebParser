import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  apiRequest,
  recommendationBackfill,
  recommendationFeedback,
  recommendationTop,
} from '../api/client.js';
import { useAuth } from '../store/auth.jsx';
import { useTheme } from '../store/theme.jsx';
import './recommendations.css';

export default function RecommendationsPage() {
  const { token, username, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();

  const [historyItems, setHistoryItems] = useState([]);
  const [historyCursor, setHistoryCursor] = useState(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');

  const [selectedItem, setSelectedItem] = useState(null);
  const [itemLoading, setItemLoading] = useState(false);
  const [itemError, setItemError] = useState('');
  const [referenceAttachmentId, setReferenceAttachmentId] = useState('');

  const [topLimit, setTopLimit] = useState('20');
  const [topLoading, setTopLoading] = useState(false);
  const [topError, setTopError] = useState('');
  const [topStatus, setTopStatus] = useState('');
  const [recommendationItems, setRecommendationItems] = useState([]);
  const [recommendationExclusions, setRecommendationExclusions] = useState([]);
  const [feedbackPending, setFeedbackPending] = useState({});
  const [feedbackNotes, setFeedbackNotes] = useState({});
  const [approvedIds, setApprovedIds] = useState([]);
  const [recommendationRunId, setRecommendationRunId] = useState('');

  const [backfillLimit, setBackfillLimit] = useState('1000');
  const [backfillLoading, setBackfillLoading] = useState(false);
  const [backfillStatus, setBackfillStatus] = useState('');
  const [backfillError, setBackfillError] = useState('');

  const imageAttachments = useMemo(() => {
    const list = selectedItem?.attachments || [];
    return list.filter((attachment) => attachment && attachment.type === 'IMAGE' && attachment.id);
  }, [selectedItem]);

  const selectedItemId = selectedItem?.id || '';

  useEffect(() => {
    void loadHistory(true);
  }, []);

  useEffect(() => {
    if (imageAttachments.length === 0) {
      setReferenceAttachmentId('');
      return;
    }
    setReferenceAttachmentId((prev) => {
      if (prev && imageAttachments.some((attachment) => attachment.id === prev)) {
        return prev;
      }
      return imageAttachments[0].id;
    });
  }, [imageAttachments]);

  const loadHistory = async (reset) => {
    setHistoryLoading(true);
    setHistoryError('');
    try {
      const cursorParam = reset ? null : historyCursor;
      const query = cursorParam ? `?limit=20&cursor=${encodeURIComponent(cursorParam)}` : '?limit=20';
      const response = await apiRequest(`/api/items${query}`, { token });
      setHistoryItems((prev) => (reset ? response.items || [] : [...prev, ...(response.items || [])]));
      setHistoryCursor(response.nextCursor || null);
    } catch (error) {
      setHistoryError(error.message || 'Не удалось загрузить историю');
    } finally {
      setHistoryLoading(false);
    }
  };

  const openItem = async (itemId) => {
    if (!itemId) {
      return;
    }
    setItemLoading(true);
    setItemError('');
    setTopError('');
    setTopStatus('');
    setRecommendationItems([]);
    setRecommendationExclusions([]);
    setRecommendationRunId('');
    setApprovedIds([]);
    try {
      const item = await apiRequest(`/api/items/${itemId}`, { token });
      setSelectedItem(item);
    } catch (error) {
      setItemError(error.message || 'Не удалось загрузить Item');
    } finally {
      setItemLoading(false);
    }
  };

  const handleBackfill = async () => {
    const parsed = Number(backfillLimit);
    const limit = Number.isFinite(parsed) && parsed > 0 ? Math.min(parsed, 4000) : 1000;
    setBackfillLoading(true);
    setBackfillError('');
    setBackfillStatus('');
    try {
      const response = await recommendationBackfill(token, limit);
      setBackfillStatus(`Backfill: scanned ${response.scanned}, upserted ${response.upserted}, ${response.durationMs} ms`);
    } catch (error) {
      setBackfillError(error.message || 'Не удалось выполнить backfill');
    } finally {
      setBackfillLoading(false);
    }
  };

  const handleTopLoad = async () => {
    if (!referenceAttachmentId) {
      setTopError('Выберите референсную картинку');
      return;
    }
    const parsed = Number(topLimit);
    const limit = Number.isFinite(parsed) && parsed > 0 ? Math.min(parsed, 50) : 20;
    setTopLoading(true);
    setTopError('');
    setTopStatus('');
    setApprovedIds([]);
    try {
      const response = await recommendationTop(token, referenceAttachmentId, limit);
      setRecommendationItems(response.candidates || []);
      setRecommendationExclusions(response.exclusions || []);
      setRecommendationRunId(response.runId || '');
      setTopStatus(`Получено кандидатов: ${response.returnedCount} · Run: ${response.runId || '—'}`);
    } catch (error) {
      setTopError(error.message || 'Не удалось загрузить рекомендации');
    } finally {
      setTopLoading(false);
    }
  };

  const handleFeedback = async (candidate, action) => {
    const candidateId = candidate?.attachmentId;
    if (!candidateId || !referenceAttachmentId) {
      return;
    }
    setFeedbackPending((prev) => ({ ...prev, [candidateId]: true }));
    setTopError('');
    try {
      const reasonByAction = {
        APPROVE: 'RELEVANT',
        REJECT: 'OFF_TOPIC',
        SKIP: 'NOT_SURE',
      };
      await recommendationFeedback(token, {
        referenceAttachmentId,
        recommendedAttachmentId: candidateId,
        action,
        reason: reasonByAction[action],
        runId: recommendationRunId,
        servedRank: candidate.rank,
        note: feedbackNotes[candidateId] || undefined,
      });
      if (action === 'APPROVE') {
        setApprovedIds((prev) => (prev.includes(candidateId) ? prev : [...prev, candidateId]));
      }
      if (action === 'REJECT') {
        setApprovedIds((prev) => prev.filter((id) => id !== candidateId));
      }
    } catch (error) {
      setTopError(error.message || 'Не удалось сохранить feedback');
    } finally {
      setFeedbackPending((prev) => {
        const next = { ...prev };
        delete next[candidateId];
        return next;
      });
    }
  };

  const formatItemDate = (value) => {
    if (!value) {
      return '—';
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return value;
    }
    return parsed.toLocaleString();
  };

  return (
    <main className="pipeline recommendations-page">
      <header className="topbar">
        <div>
          <p className="eyebrow">AK Content Pipeline</p>
          <h1>Recommendations workflow</h1>
        </div>
        <div className="user-box">
          <button type="button" onClick={toggleTheme} className="ghost">
            Тема: {theme === 'dark' ? 'тёмная' : 'светлая'}
          </button>
          <span>{username || 'user'}</span>
          <Link className="ghost" to="/pipeline">Manual</Link>
          <button type="button" onClick={logout} className="ghost">Выйти</button>
        </div>
      </header>

      <section className="stepper-card">
        <div className="panel rec-page">
          <div className="rec-intro">
            <h2>Режим: полуавтомат</h2>
            <p className="muted">
              Отдельный контур для рекомендаций. Manual workflow не затрагивается.
            </p>
            <div className="rec-mode-strip">
              <span className="rec-mode active">SEMI_AUTO</span>
              <span className="rec-mode muted-chip">AUTO_POST (позже)</span>
            </div>
          </div>

          <div className="rec-workspace">
            <aside className="rec-column rec-sidebar">
              <div className="rec-card">
                <h3>1. Подготовка feature-store</h3>
                <p className="muted">Запускается вручную при необходимости.</p>
                <div className="rec-input-row">
                  <label>
                    Backfill limit
                    <input
                      type="number"
                      min="1"
                      max="4000"
                      value={backfillLimit}
                      onChange={(event) => setBackfillLimit(event.target.value)}
                    />
                  </label>
                  <button type="button" className="ghost" onClick={handleBackfill} disabled={backfillLoading}>
                    {backfillLoading ? 'Backfill...' : 'Backfill'}
                  </button>
                </div>
                {backfillStatus ? <p className="muted">{backfillStatus}</p> : null}
                {backfillError ? <p className="error">{backfillError}</p> : null}
              </div>

              <div className="rec-card">
                <div className="rec-section-head">
                  <h3>2. Выберите Item</h3>
                  <button type="button" className="ghost" onClick={() => loadHistory(true)} disabled={historyLoading}>
                    Обновить
                  </button>
                </div>
                {historyError ? <p className="error">{historyError}</p> : null}
                {historyLoading && historyItems.length === 0 ? <p className="muted">Загрузка...</p> : null}
                {!historyLoading && historyItems.length === 0 ? (
                  <div className="empty">История Item пока пуста.</div>
                ) : null}
                <div className="rec-item-list">
                  {historyItems.map((item) => {
                    const active = selectedItemId === item.id;
                    return (
                      <button
                        key={item.id}
                        type="button"
                        className={`rec-item-card ${active ? 'active' : ''}`}
                        onClick={() => openItem(item.id)}
                        disabled={itemLoading}
                      >
                        <span className="rec-item-title">{item.title || item.sourceUrl || item.id}</span>
                        <span className="muted">
                          {formatItemDate(item.createdAt)} · {item.sourceType} · вложений: {item.attachments?.length || 0}
                        </span>
                      </button>
                    );
                  })}
                </div>
                {historyCursor ? (
                  <button type="button" className="ghost" onClick={() => loadHistory(false)} disabled={historyLoading}>
                    Показать ещё
                  </button>
                ) : null}
              </div>
            </aside>

            <div className="rec-column rec-main">
              <div className="rec-card">
                <div className="rec-section-head">
                  <h3>3. Референсное изображение</h3>
                  <span className="muted">{selectedItem ? `Item: ${selectedItem.id}` : 'Item не выбран'}</span>
                </div>
                {itemError ? <p className="error">{itemError}</p> : null}
                {itemLoading ? <p className="muted">Загрузка Item...</p> : null}

                {!selectedItem ? (
                  <div className="empty">Сначала выберите Item в левой колонке.</div>
                ) : null}
                {selectedItem && imageAttachments.length === 0 ? (
                  <div className="empty">В выбранном Item нет IMAGE-вложений.</div>
                ) : null}

                {selectedItem && imageAttachments.length > 0 ? (
                  <>
                    <div className="rec-reference-grid">
                      {imageAttachments.map((attachment, index) => {
                        const active = attachment.id === referenceAttachmentId;
                        return (
                          <button
                            key={attachment.id}
                            type="button"
                            className={`rec-reference-card ${active ? 'active' : ''}`}
                            onClick={() => setReferenceAttachmentId(attachment.id)}
                          >
                            <img src={attachment.url} alt="" loading="lazy" />
                            <span className="rec-reference-index">#{index + 1}</span>
                          </button>
                        );
                      })}
                    </div>
                    <div className="rec-input-row">
                      <label>
                        Top-N limit
                        <input
                          type="number"
                          min="1"
                          max="50"
                          value={topLimit}
                          onChange={(event) => setTopLimit(event.target.value)}
                        />
                      </label>
                      <button
                        type="button"
                        onClick={handleTopLoad}
                        disabled={topLoading || !referenceAttachmentId}
                      >
                        {topLoading ? 'Подбор...' : 'Подобрать top-N'}
                      </button>
                    </div>
                    <p className="muted">Выбран reference: {referenceAttachmentId || '—'}</p>
                  </>
                ) : null}
                {topStatus ? <p className="muted">{topStatus}</p> : null}
                {topError ? <p className="error">{topError}</p> : null}
              </div>

              <div className="rec-card">
                <div className="rec-section-head">
                  <h3>4. Кандидаты</h3>
                  <span className="muted">APPROVE: {approvedIds.length} / {recommendationItems.length}</span>
                </div>
                {recommendationItems.length === 0 ? (
                  <div className="empty">Запустите top-N после выбора референса.</div>
                ) : (
                  <div className="recommendation-grid">
                    {recommendationItems.map((candidate) => {
                      const candidateId = candidate.attachmentId || candidate.imageUrl;
                      const isApproved = candidate.attachmentId && approvedIds.includes(candidate.attachmentId);
                      const pending = candidate.attachmentId && feedbackPending[candidate.attachmentId];
                      return (
                        <div key={candidateId} className={`recommendation-card ${isApproved ? 'selected' : ''}`}>
                          <div className="recommendation-preview">
                            <img src={candidate.imageUrl} alt="" loading="lazy" />
                          </div>
                          <div className="recommendation-meta">
                            <p>Score: {candidate.score}</p>
                            <p className="muted">Visual: {candidate.visualScore} · History: {candidate.historyScore}</p>
                            <p className="muted">Reason: {candidate.reason}</p>
                            <p className="muted">
                              Rank: {candidate.rank || '—'} · Diversity penalty: {candidate.diversityPenalty ?? '—'}
                            </p>
                            <p className="muted">
                              Analysis: {candidate.explanation?.analysisVersion || 'legacy'} ·
                              rules: {candidate.explanation?.rankingVersion || 'legacy'}
                            </p>
                            <label>
                              Причина / заметка модератора
                              <input
                                type="text"
                                maxLength="1000"
                                value={feedbackNotes[candidateId] || ''}
                                onChange={(event) => setFeedbackNotes((prev) => ({
                                  ...prev,
                                  [candidateId]: event.target.value,
                                }))}
                                placeholder="Необязательно; reason code сохранится автоматически"
                              />
                            </label>
                          </div>
                          <div className="recommendation-card-actions">
                            <button
                              type="button"
                              className="ghost"
                              onClick={() => handleFeedback(candidate, 'APPROVE')}
                              disabled={pending || !candidate.attachmentId}
                            >
                              {pending ? '...' : isApproved ? 'Approved ✓' : 'Approve'}
                            </button>
                            <button
                              type="button"
                              className="ghost"
                              onClick={() => handleFeedback(candidate, 'SKIP')}
                              disabled={pending || !candidate.attachmentId}
                            >
                              Skip
                            </button>
                            <button
                              type="button"
                              className="danger"
                              onClick={() => handleFeedback(candidate, 'REJECT')}
                              disabled={pending || !candidate.attachmentId}
                            >
                              Reject
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
                {recommendationExclusions.length > 0 ? (
                  <div>
                    <h4>Исключено правилами: {recommendationExclusions.length}</h4>
                    <ul>
                      {recommendationExclusions.map((exclusion) => (
                        <li key={`${exclusion.attachmentId}-${exclusion.rule}`}>
                          <code>{exclusion.rule}</code>
                          {' · '}
                          {exclusion.attachmentId}
                          {' · '}
                          {exclusion.evidence || 'без дополнительного evidence'}
                          {' · analysis: '}
                          {exclusion.analysisVersion || 'legacy'}
                        </li>
                      ))}
                    </ul>
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
