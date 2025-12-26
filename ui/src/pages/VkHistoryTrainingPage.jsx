import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  Container,
  LinearProgress,
  Link as MuiLink,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
  useTheme,
  useScrollTrigger,
} from "@mui/material";
import { toast } from "react-toastify";
import vkHistoryService from "../service/vkHistoryService";

const PAGE_SIZE = 50;
const FILTER_ALL = "all";
const FILTER_TRAINING = "training";
const FILTER_EXCLUDED = "excluded";

export default function VkHistoryTrainingPage() {
  const [filter, setFilter] = useState(FILTER_ALL);
  const [sinceInput, setSinceInput] = useState("");
  const [activeSince, setActiveSince] = useState("");
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [error, setError] = useState(null);
  const [lastLoadedAt, setLastLoadedAt] = useState(null);
  const [pagination, setPagination] = useState({ limit: PAGE_SIZE, offset: 0, total: 0 });
  const [syncStatus, setSyncStatus] = useState(null);
  const [syncPolling, setSyncPolling] = useState(false);
  const [syncError, setSyncError] = useState(null);

  const theme = useTheme();
  const trigger = useScrollTrigger({ disableHysteresis: true, threshold: 0 });
  const appBarH = theme.mixins.toolbar?.minHeight || 64;
  const headerH = 64;
  const headerTop = trigger ? 0 : appBarH;

  const sentinelRef = useRef(null);
  const offsetRef = useRef(0);
  const hasMoreRef = useRef(true);
  const loadingRef = useRef(false);
  const loadingMoreRef = useRef(false);

  const fetchSyncStatus = useCallback(async () => {
    try {
      const response = await vkHistoryService.syncStatus();
      setSyncStatus(response.data);
      setSyncError(null);
      if (response.data?.running === false) {
        setSyncPolling(false);
      }
    } catch (e) {
      setSyncError(e?.message || "Не удалось получить статус синхронизации");
      setSyncPolling(false);
    }
  }, []);

  const loadPage = useCallback(async (offsetParam, reset = false) => {
    setError(null);
    if (reset) {
      loadingRef.current = true;
      setLoading(true);
    } else {
      loadingMoreRef.current = true;
      setLoadingMore(true);
    }

    try {
      const resolvedFilter =
        filter === FILTER_TRAINING ? true : filter === FILTER_EXCLUDED ? false : undefined;
      const normalizedSince = activeSince?.trim();
      const safeOffset = Math.max(0, offsetParam);

      const params = {
        limit: PAGE_SIZE,
        offset: safeOffset,
      };
      if (resolvedFilter !== undefined) {
        params.useForTraining = resolvedFilter;
      }
      if (normalizedSince) {
        params.since = normalizedSince;
      }

      const response = await vkHistoryService.entriesPage(params);
      const data = response.data || {};
      const fetched = Array.isArray(data.items) ? data.items : [];
      setItems((prev) => (reset ? fetched : [...prev, ...fetched]));
      const nextOffset = safeOffset + fetched.length;
      const total = typeof data.total === "number" ? data.total : 0;
      setPagination({ limit: PAGE_SIZE, offset: nextOffset, total });
      offsetRef.current = nextOffset;
      hasMoreRef.current = nextOffset < total;
      setHasMore(nextOffset < total);
      setLastLoadedAt(new Date());
    } catch (e) {
      setError(e?.message || "Не удалось загрузить записи");
    } finally {
      if (reset) {
        setLoading(false);
        loadingRef.current = false;
      }
      setLoadingMore(false);
      loadingMoreRef.current = false;
    }
  }, [filter, activeSince]);

  useEffect(() => {
    setItems([]);
    setPagination({ limit: PAGE_SIZE, offset: 0, total: 0 });
    setHasMore(true);
    offsetRef.current = 0;
    hasMoreRef.current = true;
    loadPage(0, true);
  }, [loadPage]);

  useEffect(() => {
    const node = sentinelRef.current;
    if (!node) {
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      if (
        entries[0]?.isIntersecting &&
        hasMoreRef.current &&
        !loadingRef.current &&
        !loadingMoreRef.current
      ) {
        loadPage(offsetRef.current, false);
      }
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [loadPage]);

  useEffect(() => {
    if (!syncPolling) {
      return;
    }
    const interval = setInterval(() => {
      fetchSyncStatus();
    }, 5000);
    return () => clearInterval(interval);
  }, [fetchSyncStatus, syncPolling]);

  const totals = useMemo(() => {
    const trainingCount = items.filter((item) => Boolean(item.useForTraining)).length;
    return {
      loaded: items.length,
      total: pagination.total,
      training: trainingCount,
    };
  }, [items, pagination.total]);

  const applySinceFilter = () => {
    setActiveSince(sinceInput.trim());
  };

  const handleToggle = async (rowIndex) => {
    const row = items[rowIndex];
    const next = !Boolean(row.useForTraining);
    const previousItems = [...items];
    const updated = [...previousItems];
    updated[rowIndex] = { ...row, useForTraining: next };
    setItems(updated);

    try {
      await vkHistoryService.updateUseForTraining(row.id, next);
      if (filter === FILTER_TRAINING && !next) {
        loadPage(0, true);
      }
    } catch (e) {
      setItems(previousItems);
      setError(e?.message || "Не удалось обновить флаг");
    }
  };

  const handleSyncWall = async () => {
    setError(null);
    try {
      await vkHistoryService.triggerSyncWall();
      setSyncPolling(true);
      fetchSyncStatus();
      toast.info("Синхронизация запущена — это может занять время");
      loadPage(0, true);
    } catch (e) {
      setError(e?.message || "Не удалось синхронизировать стену");
    }
  };

  const handleRefresh = () => {
    loadPage(0, true);
  };

  return (
    <>
      <Box
        sx={{
          position: "fixed",
          top: headerTop,
          left: 0,
          right: 0,
          height: headerH,
          bgcolor: "background.paper",
          borderBottom: 1,
          borderColor: "divider",
          zIndex: theme.zIndex.appBar - 1,
          px: 2,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 2,
          flexWrap: { xs: "wrap", md: "nowrap" },
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 2, flexWrap: "wrap" }}>
          <ToggleButtonGroup
            value={filter}
            exclusive
            onChange={(_, next) => {
              if (next) {
                setFilter(next);
              }
            }}
            size="small"
          >
            <ToggleButton value={FILTER_ALL}>Все</ToggleButton>
            <ToggleButton value={FILTER_TRAINING}>Для обучения</ToggleButton>
            <ToggleButton value={FILTER_EXCLUDED}>Исключённые</ToggleButton>
          </ToggleButtonGroup>
          <TextField
            size="small"
            label="С (ISO)"
            placeholder="2024-01-01T00:00:00Z"
            value={sinceInput}
            onChange={(event) => setSinceInput(event.target.value)}
          />
          <Button variant="outlined" size="small" onClick={applySinceFilter} disabled={loading}>
            Применить
          </Button>
          <Typography variant="body2" color="text.secondary">
            Показано {totals.loaded} из {totals.total}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Обновлено:{" "}
            {lastLoadedAt
              ? lastLoadedAt.toLocaleTimeString([], {
                  hour: "2-digit",
                  minute: "2-digit",
                  second: "2-digit",
                })
              : "—"}
          </Typography>
        </Box>
        <Box sx={{ display: "flex", gap: 1 }}>
          <Button variant="outlined" onClick={handleRefresh} disabled={loading}>
            Обновить
          </Button>
          <Button variant="outlined" onClick={handleSyncWall} disabled={loading}>
            Синхронизировать стену
          </Button>
        </Box>
      </Box>

      <Container
        maxWidth={false}
        disableGutters
        sx={{ pt: `${appBarH + headerH}px`, px: 2, pb: 4 }}
      >
        <Typography variant="h4" gutterBottom>
          История VK / Обучающий датасет
        </Typography>

        {syncError && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            {syncError}
          </Alert>
        )}
        {syncStatus && (
          <Alert severity={syncStatus.running ? "info" : "success"} sx={{ mb: 2 }}>
            {syncStatus.running
              ? "Синхронизация выполняется..."
              : `Синхронизация завершена. Добавлено: ${syncStatus.lastReport?.inserted ?? 0}`}
          </Alert>
        )}
        {syncStatus?.lastError && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            Ошибка синхронизации: {syncStatus.lastError}
          </Alert>
        )}

        {loading && <LinearProgress sx={{ mb: 2 }} />}

        {error && (
          <Alert severity="error" sx={{ my: 2 }}>
            {error}
          </Alert>
        )}

        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Превью</TableCell>
                <TableCell>URL</TableCell>
                <TableCell>ML decision</TableCell>
                <TableCell>Score</TableCell>
                <TableCell>Reason</TableCell>
                <TableCell align="center">Use for training</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((row, idx) => (
                <TableRow key={row.id ?? `${row.hash}-${idx}`}>
                  <TableCell>{row.id ?? "—"}</TableCell>
                  <TableCell>
                    <img
                      src={row.url}
                      alt={row.hash}
                      style={{ width: 120, height: 80, objectFit: "cover" }}
                      onError={(e) => (e.currentTarget.style.visibility = "hidden")}
                    />
                  </TableCell>
                  <TableCell>
                    <MuiLink href={row.url} target="_blank" rel="noreferrer">
                      {row.url}
                    </MuiLink>
                  </TableCell>
                  <TableCell>{row.mlDecision ?? "—"}</TableCell>
                  <TableCell>{row.mlScore != null ? row.mlScore.toFixed(3) : "—"}</TableCell>
                  <TableCell>{row.mlReason ?? "—"}</TableCell>
                <TableCell align="center">
                  <Checkbox
                    checked={Boolean(row.useForTraining)}
                    onChange={() => handleToggle(idx)}
                    size="small"
                    inputProps={{ "aria-label": "use-for-training" }}
                  />
                </TableCell>
                </TableRow>
              ))}
              {items.length === 0 && !loading && !error && (
                <TableRow>
                  <TableCell colSpan={7}>
                    <Alert severity="info">Записей нет.</Alert>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>

        <Box
          ref={sentinelRef}
          sx={{
            display: "flex",
            justifyContent: "center",
            mt: 2,
            minHeight: 32,
          }}
        >
          {loadingMore && <CircularProgress size={24} />}
        </Box>
      </Container>
    </>
  );
}
