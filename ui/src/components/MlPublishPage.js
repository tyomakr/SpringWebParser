import React, { useMemo, useState } from "react";
import { observer } from "mobx-react";
import { toast } from "react-toastify";
import {
    Alert,
    Box,
    Button,
    Checkbox,
    CircularProgress,
    Container,
    FormControlLabel,
    IconButton,
    Paper,
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

    const theme = useTheme();
    const trigger = useScrollTrigger({ disableHysteresis: true, threshold: 0 });
    const appBarH = theme.mixins.toolbar?.minHeight || 64;
    const headerH = 64;
    const headerTop = trigger ? 0 : appBarH;

    const formatTimestamp = (date) =>
        date ? date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" }) : "—";

    const availableImages = storeFI.webImages.slice();
    const filteredPreviewItems = useMemo(() => {
        if (decisionFilter === "ALL") {
            return previewItems;
        }
        return previewItems.filter((item) => item.decision === decisionFilter);
    }, [previewItems, decisionFilter]);
    const publishCount = useMemo(
        () => previewItems.filter((item) => item.publish).length,
        [previewItems]
    );
    const skipCount = useMemo(
        () => previewItems.length - publishCount,
        [previewItems, publishCount]
    );

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

        setLoadingPreview(true);
        setPreviewError(null);
        setPreviewEmpty(false);
        setCommitResult(null);

        try {
            const response = await mlPublishService.preview(payload);
            const list = response.data.recommendations.map((item) => ({
                ...item,
                publish: item.decision === "PUBLISH",
            }));

            if (!list.length) {
                setPreviewEmpty(true);
                setPreviewItems([]);
            } else {
                setPreviewItems(list);
            }
            setLastPreviewAt(new Date());
        } catch (err) {
            setPreviewError(err?.message || "Ошибка запроса ML-превью");
            setPreviewItems([]);
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
                                    <TableCell>Decision</TableCell>
                                    <TableCell>Reason</TableCell>
                                    <TableCell>Publish</TableCell>
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
                                        </TableRow>
                                    );
                                })}
                            </TableBody>
                        </Table>
                    </TableContainer>
                )}
            </Paper>

            <Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
                <Button
                    variant="contained"
                    color="success"
                    onClick={handleCommit}
                    disabled={!previewItems.length || loadingCommit}
                >
                    {loadingCommit ? <CircularProgress size={18} /> : "Опубликовать выбранные"}
                </Button>
                {commitError && <Alert severity="error">{commitError}</Alert>}
                {commitResult && (
                    <Alert severity="success">
                        Залито: {commitResult.uploadedCount}, опубликовано: {commitResult.publishedCount}.
                    </Alert>
                )}
            </Box>
        </Container>
        </>
    );
});

export default MlPublishPage;
