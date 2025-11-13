import React, { useCallback, useEffect, useMemo, useState } from "react";
import { observer } from "mobx-react";
import { toast } from "react-toastify";
import {
    Alert,
    Box,
    Button,
    Checkbox,
    Chip,
    CircularProgress,
    Container,
    FormControlLabel,
    IconButton,
    Paper,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
    Tooltip,
    ToggleButtonGroup,
    ToggleButton,
    useTheme,
    useScrollTrigger,
} from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import storeFI from "../store/StoreFI";
import mlPublishService from "../service/mlPublishService";
import vkHistoryService from "../service/vkHistoryService";

const MlPublishPage = observer(() => {
    const [fromPage, setFromPage] = useState(1);
    const [toPage, setToPage] = useState(5);
    const [loadingFetch, setLoadingFetch] = useState(false);
    const [previewItems, setPreviewItems] = useState([]);
    const [loadingPreview, setLoadingPreview] = useState(false);
    const [previewError, setPreviewError] = useState(null);
    const [previewEmpty, setPreviewEmpty] = useState(false);
    const [commitResult, setCommitResult] = useState(null);
    const [loadingCommit, setLoadingCommit] = useState(false);
    const [commitError, setCommitError] = useState(null);
    const [lastFetchAt, setLastFetchAt] = useState(null);
    const [lastPreviewAt, setLastPreviewAt] = useState(null);
    const [decisionFilter, setDecisionFilter] = useState("ALL");
    const [showUncertainOnly, setShowUncertainOnly] = useState(false);
    const [mlPublishConfig, setMlPublishConfig] = useState({
        apiKeyConfigured: true,
        requireApiKey: false,
        maxBatchSize: 100,
        mlServiceConfig: { phashMaxDist: 12, grayBand: 4 },
    });
    const [mlStatus, setMlStatus] = useState(null);
    const [statusLoading, setStatusLoading] = useState(false);
    const [statusLastCheckedAt, setStatusLastCheckedAt] = useState(0);
    const [previewMetrics, setPreviewMetrics] = useState({ processed: 0, durationMs: 0 });
    const STATUS_DEBOUNCE_MS = 15000;
    const [feedbackResult, setFeedbackResult] = useState(null);
    const [feedbackError, setFeedbackError] = useState(null);
    const [loadingFeedback, setLoadingFeedback] = useState(false);

    const theme = useTheme();
    const trigger = useScrollTrigger({ disableHysteresis: true, threshold: 0 });
    const appBarH = theme.mixins.toolbar?.minHeight || 64;
    const headerH = 64;
    const headerTop = trigger ? 0 : appBarH;

    const formatTimestamp = (date) =>
        date ? date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" }) : "—";

    const availableImages = storeFI.webImages.slice();
    const filteredPreviewItems = useMemo(() => {
        let items = previewItems;
        if (decisionFilter !== "ALL") {
            items = items.filter((item) => item.decision === decisionFilter);
        }
        if (showUncertainOnly) {
            items = items.filter((item) => item.zone === "gray");
        }
        return items;
    }, [previewItems, decisionFilter, showUncertainOnly]);
    useEffect(() => {
        let mounted = true;
        mlPublishService.config()
            .then((response) => {
                if (mounted && response?.data) {
                    setMlPublishConfig((prev) => ({
                        ...prev,
                        ...response.data,
                        mlServiceConfig: response.data?.mlServiceConfig ?? prev.mlServiceConfig,
                    }));
                }
            })
            .catch(() => {
                // keep defaults
            });
        return () => {
            mounted = false;
        };
    }, []);
    const publishCount = useMemo(
        () => previewItems.filter((item) => item.publish).length,
        [previewItems]
    );
    const skipCount = useMemo(
        () => previewItems.length - publishCount,
        [previewItems, publishCount]
    );

    const parseCandidateId = (value) => {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    };

    const handleFetch = async () => {
        if (fromPage > toPage) {
            toast.error("Страница от должна быть меньше или равна странице до");
            return;
        }

        setLoadingFetch(true);
        try {
            await storeFI.getWebImagesFromPages(fromPage, toPage);
            toast.success(`Загружено ${storeFI.webImages.length} кандидатов`);
            setLastFetchAt(new Date());
        } catch (e) {
            toast.error("Не удалось загрузить кандидатов");
        } finally {
            setLoadingFetch(false);
        }
    };

    const checkMlStatus = useCallback(async (force = false) => {
        const now = Date.now();
        if (!force && now - statusLastCheckedAt < STATUS_DEBOUNCE_MS) {
            return;
        }
        setStatusLoading(true);
        try {
            const response = await mlPublishService.status();
            setMlStatus(response.data);
        } catch (error) {
            setMlStatus({
                mlReachable: false,
                indexSize: null,
                config: null,
                error: error?.message || "Unknown",
            });
        } finally {
            setStatusLastCheckedAt(now);
            setStatusLoading(false);
        }
    }, [statusLastCheckedAt]);

    const handleCheckStatus = () => {
        checkMlStatus(true);
    };

    const renderStatusBanner = () => {
        if (!previewEmpty) {
            return null;
        }
        if (!mlStatus && statusLoading) {
            return (
                <Alert severity="info" sx={{ mb: 2 }}>
                    ML service status check in progress...
                </Alert>
            );
        }
        if (!mlStatus) {
            return null;
        }
        if (!mlStatus.mlReachable) {
            return (
                <Alert severity="warning" sx={{ mb: 2 }}>
                    ML service is unreachable/timeout. Check the ml-service container{mlStatus.error ? ` (${mlStatus.error})` : ""}.
                </Alert>
            );
        }
        if (mlStatus.indexSize === 0) {
            return (
                <Alert severity="info" sx={{ mb: 2 }}>
                    Training index is empty. Run VK wall sync.
                </Alert>
            );
        }
        if (mlStatus.indexSize > 0) {
            return (
                <Alert severity="info" sx={{ mb: 2 }}>
                    No recommendations for given candidates. Try a different batch or adjust thresholds.
                </Alert>
            );
        }
        return null;
    };

    const buildPreviewPayload = () =>
        availableImages.map((image) => ({
            id: image.id || image.directLink,
            url: image.directLink,
        }));

    const handlePreview = async () => {
        const payload = buildPreviewPayload();
        if (!payload.length) {
            setPreviewError("Нет кандидатов для ML-превью");
            setPreviewItems([]);
            return;
        }

        const start = Date.now();
        setLoadingPreview(true);
        setPreviewError(null);
        setPreviewEmpty(false);
        setCommitResult(null);

        try {
            const response = await mlPublishService.preview(payload);
            const recommendations = response.data?.recommendations ?? [];
            const list = recommendations.map((item) => ({
                ...item,
                publish: item.decision === "PUBLISH",
                exclude: false,
            }));
            setPreviewMetrics({
                processed: payload.length,
                durationMs: Date.now() - start,
            });

            if (!list.length) {
                setPreviewEmpty(true);
                setPreviewItems([]);
                await checkMlStatus();
            } else {
                setPreviewItems(list);
                setPreviewEmpty(false);
            }
            setLastPreviewAt(new Date());
        } catch (err) {
            setPreviewError(err?.message || "Ошибка запроса ML-превью");
            setPreviewItems([]);
            setPreviewEmpty(true);
            await checkMlStatus();
        } finally {
            setLoadingPreview(false);
        }
    };

    const handleTogglePublish = (index) => {
        setPreviewItems((prev) => {
            const next = [...prev];
            next[index] = {
                ...next[index],
                publish: !next[index].publish,
            };
            return next;
        });
    };

    const handlePublishAll = (value) => {
        setPreviewItems((prev) =>
            prev.map((item) => ({
                ...item,
                publish: value,
            }))
        );
    };

    const handleExcludeFromTraining = async (index) => {
        const candidate = previewItems[index];
        if (!candidate) {
            return;
        }
        const toggled = !candidate.exclude;
        setPreviewItems((prev) => {
            const next = [...prev];
            next[index] = { ...candidate, exclude: toggled };
            return next;
        });

        if (toggled) {
            const historyId = parseCandidateId(candidate.id);
            if (historyId != null) {
                try {
                    await vkHistoryService.updateUseForTraining(historyId, false);
                } catch (_error) {
                    toast.error("Не удалось обновить флаг исключения");
                    setPreviewItems((prev) => {
                        const next = [...prev];
                        next[index] = { ...candidate, exclude: false };
                        return next;
                    });
                }
            }
        }
    };

    const buildFeedbackPayload = () =>
        previewItems.map((item) => ({
            candidateId: parseCandidateId(item.id),
            url: item.url,
            hash: item.hash,
            decision: item.exclude ? "EXCLUDE" : item.publish ? "PUBLISH" : "SKIP",
            score: item.score,
            reason: item.reason,
            zone: item.zone,
        }));

    const handleSaveFeedback = async () => {
        if (!previewItems.length) {
            return;
        }
        setLoadingFeedback(true);
        setFeedbackResult(null);
        setFeedbackError(null);
        try {
            const response = await mlPublishService.feedback(buildFeedbackPayload());
            setFeedbackResult(response.data);
            toast.success(`Сохранено ${response.data?.saved ?? 0} фидбэков`);
        } catch (err) {
            setFeedbackError(err?.message || "Не удалось сохранить фидбэк");
        } finally {
            setLoadingFeedback(false);
        }
    };

    const handleFilterChange = (_event, nextValue) => {
        if (nextValue) {
            setDecisionFilter(nextValue);
        }
    };

    const handleCommit = async () => {
        if (!previewItems.length) {
            return;
        }

        setLoadingCommit(true);
        setCommitError(null);

        try {
            const response = await mlPublishService.commit(previewItems);
            setCommitResult(response.data);
        } catch (err) {
            setCommitError(err?.message || "Ошибка публикации");
        } finally {
            setLoadingCommit(false);
        }
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
                <Box
                    sx={{
                        display: "flex",
                        alignItems: "baseline",
                        gap: 1.5,
                        flexWrap: "wrap",
                    }}
                >
                    <Box sx={{ display: "flex", alignItems: "baseline", gap: 0.5 }}>
                        <Typography variant="body2" color="text.secondary">
                            Кандидатов:
                        </Typography>
                        <Typography variant="h6">{availableImages.length}</Typography>
                    </Box>
                    <Box sx={{ display: "flex", alignItems: "baseline", gap: 0.5 }}>
                        <Typography variant="body2" color="text.secondary">
                            Publish:
                        </Typography>
                        <Typography variant="h6">{publishCount}</Typography>
                    </Box>
                    <Box sx={{ display: "flex", alignItems: "baseline", gap: 0.5 }}>
                        <Typography variant="body2" color="text.secondary">
                            Skip:
                        </Typography>
                        <Typography variant="h6">{skipCount}</Typography>
                    </Box>
                </Box>
                <Box sx={{ display: "flex", gap: 1 }}>
                    <Button
                        variant="outlined"
                        size="small"
                        disabled={!previewItems.length}
                        onClick={() => handlePublishAll(true)}
                    >
                        Publish all
                    </Button>
                    <Button
                        variant="outlined"
                        size="small"
                        disabled={!previewItems.length}
                        onClick={() => handlePublishAll(false)}
                    >
                        Снять выбор
                    </Button>
                </Box>
            </Box>

            <Container
                maxWidth={false}
                disableGutters
                sx={{ pt: `${appBarH + headerH}px`, px: 2, pb: 4 }}
            >
                <Typography variant="h4" gutterBottom>
                    ML-публикация
                </Typography>
                {!mlPublishConfig.apiKeyConfigured && (
                    <Alert severity="info" sx={{ mb: 2 }}>
                        ML export auth is disabled (dev).
                    </Alert>
                )}
                {renderStatusBanner()}
                <Box
                    sx={{
                        display: "flex",
                        flexWrap: "wrap",
                        gap: 2,
                        alignItems: "center",
                        mb: 3,
                    }}
                >
                    <Typography variant="caption" color="text.secondary">
                        Кандидаты обновлены: {formatTimestamp(lastFetchAt)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        ML-превью обновлено: {formatTimestamp(lastPreviewAt)}
                    </Typography>
                    {previewMetrics.processed > 0 && (
                        <Typography variant="caption" color="text.secondary">
                            Обработано {previewMetrics.processed} кандидатов за {previewMetrics.durationMs} мс
                        </Typography>
                    )}
                    <ToggleButtonGroup
                        size="small"
                        value={decisionFilter}
                        exclusive
                        onChange={handleFilterChange}
                    >
                        <ToggleButton value="ALL">All</ToggleButton>
                        <ToggleButton value="PUBLISH">Publish</ToggleButton>
                        <ToggleButton value="SKIP">Skip</ToggleButton>
                    </ToggleButtonGroup>
                    <FormControlLabel
                        control={
                            <Switch
                                size="small"
                                checked={showUncertainOnly}
                                onChange={(event) => setShowUncertainOnly(event.target.checked)}
                            />
                        }
                        label="Show uncertain only"
                    />
                    <Typography variant="caption" color="text.secondary">
                        phashMaxDist: {mlPublishConfig.mlServiceConfig?.phashMaxDist} · grayBand:{" "}
                        {mlPublishConfig.mlServiceConfig?.grayBand}
                    </Typography>
                    <Button
                        variant="outlined"
                        size="small"
                        onClick={handleCheckStatus}
                        disabled={statusLoading}
                        startIcon={
                            statusLoading ? <CircularProgress size={16} /> : null
                        }
                    >
                        Check ML status
                    </Button>
                </Box>

                <Paper sx={{ p: 3, mb: 4 }}>
                <Typography variant="h6">Загрузка кандидатов</Typography>
                <Box sx={{ display: "flex", gap: 2, mt: 2, flexWrap: "wrap" }}>
                    <TextField
                        label="Страница от"
                        type="number"
                        value={fromPage}
                        onChange={(event) => setFromPage(Number(event.target.value))}
                    />
                    <TextField
                        label="Страница до"
                        type="number"
                        value={toPage}
                        onChange={(event) => setToPage(Number(event.target.value))}
                    />
                    <Button variant="contained" disabled={loadingFetch} onClick={handleFetch}>
                        {loadingFetch ? <CircularProgress size={18} /> : "Загрузить кандидатов"}
                    </Button>
                </Box>
                <Typography sx={{ mt: 1 }}>
                    Кандидатов загружено: {availableImages.length}
                </Typography>
            </Paper>

            <Paper sx={{ p: 3, mb: 4 }}>
                <Typography variant="h6">ML-превью</Typography>
                <Button
                    variant="contained"
                    sx={{ mt: 2 }}
                    onClick={handlePreview}
                    disabled={loadingPreview || !availableImages.length}
                >
                    {loadingPreview ? <CircularProgress size={18} /> : "Запросить ML-превью"}
                </Button>

                {previewError && (
                    <Alert severity="error" sx={{ mt: 2 }}>
                        {previewError}
                    </Alert>
                )}
                {previewEmpty && (
                    <Alert severity="info" sx={{ mt: 2 }}>
                        ML-сервис вернул пустой список рекомендаций.
                    </Alert>
                )}

                {previewItems.length > 0 && (
                    <TableContainer component={Paper} sx={{ mt: 3 }}>
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell>Картинка</TableCell>
                                    <TableCell>Score</TableCell>
                                    <TableCell>Zone</TableCell>
                                    <TableCell>Decision</TableCell>
                                    <TableCell>Reason</TableCell>
                                    <TableCell>Publish</TableCell>
                                    <TableCell>Actions</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {filteredPreviewItems.map((item) => {
                                    const originalIndex = previewItems.findIndex(
                                        (candidate) => candidate.id === item.id
                                    );
                                    if (originalIndex === -1) {
                                        return null;
                                    }

                                    return (
                                        <TableRow key={item.id}>
                                            <TableCell>
                                                <img
                                                    src={item.url}
                                                    alt={item.id}
                                                    style={{ width: 120, height: 80, objectFit: "cover" }}
                                                />
                                            </TableCell>
                                            <TableCell>{item.score.toFixed(3)}</TableCell>
                                            <TableCell>
                                                {item.zone === "gray" ? (
                                                    <Chip label="Uncertain" color="warning" size="small" />
                                                ) : (
                                                    <Typography variant="body2" color="text.secondary">
                                                        {item.zone ?? "—"}
                                                    </Typography>
                                                )}
                                            </TableCell>
                                            <TableCell>{item.decision}</TableCell>
                                            <TableCell>
                                                <Box
                                                    sx={{
                                                        display: "flex",
                                                        alignItems: "center",
                                                        gap: 1,
                                                        maxWidth: 260,
                                                    }}
                                                >
                                                    <Typography variant="body2" noWrap>
                                                        {item.reason}
                                                    </Typography>
                                                    <Tooltip title={item.reason}>
                                                        <IconButton size="small">
                                                            <InfoOutlinedIcon fontSize="inherit" />
                                                        </IconButton>
                                                    </Tooltip>
                                                </Box>
                                                {item.zone === "gray" && (
                                                    <Chip label="Uncertain" color="warning" size="small" />
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                <FormControlLabel
                                                    control={
                                                        <Checkbox
                                                            checked={item.publish}
                                                            onChange={() => handleTogglePublish(originalIndex)}
                                                            size="small"
                                                        />
                                                    }
                                                    label=""
                                                />
                                            </TableCell>
                                            <TableCell>
                                                <Button
                                                    variant={item.exclude ? "contained" : "text"}
                                                    size="small"
                                                    color={item.exclude ? "secondary" : "inherit"}
                                                    onClick={() => handleExcludeFromTraining(originalIndex)}
                                                >
                                                    {item.exclude ? "Excluded" : "Exclude from training"}
                                                </Button>
                                            </TableCell>
                                        </TableRow>
                                    );
                                })}
                            </TableBody>
                        </Table>
                    </TableContainer>
                )}
            </Paper>

            <Box sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}>
                <Button
                    variant="contained"
                    color="success"
                    onClick={handleCommit}
                    disabled={!previewItems.length || loadingCommit}
                >
                    {loadingCommit ? <CircularProgress size={18} /> : "Опубликовать выбранные"}
                </Button>
                <Button
                    variant="outlined"
                    color="info"
                    onClick={handleSaveFeedback}
                    disabled={!previewItems.length || loadingFeedback}
                >
                    {loadingFeedback ? <CircularProgress size={18} /> : "Save feedback"}
                </Button>
                {commitError && <Alert severity="error">{commitError}</Alert>}
                {commitResult && (
                    <Alert severity="success">
                        Залито: {commitResult.uploadedCount}, опубликовано: {commitResult.publishedCount}.
                    </Alert>
                )}
                {feedbackError && (
                    <Alert severity="error">
                        {feedbackError}
                    </Alert>
                )}
                {feedbackResult && (
                    <Alert severity="info">
                        Сохранено фидбэков: {feedbackResult.saved}
                    </Alert>
                )}
            </Box>
        </Container>
        </>
    );
});

export default MlPublishPage;
