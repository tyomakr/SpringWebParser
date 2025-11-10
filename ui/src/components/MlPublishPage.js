import React, { useState } from "react";
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
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
} from "@mui/material";
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

    const availableImages = storeFI.webImages.slice();

    const handleFetch = async () => {
        if (fromPage > toPage) {
            toast.error("Страница от должна быть меньше или равна странице до");
            return;
        }

        setLoadingFetch(true);
        try {
            await storeFI.getWebImagesFromPages(fromPage, toPage);
            toast.success(`Загружено ${storeFI.webImages.length} кандидатов`);
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
        <Container sx={{ py: 4 }}>
            <Typography variant="h4" gutterBottom>
                ML-публикация
            </Typography>

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
                                {previewItems.map((item, index) => (
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
                                        <TableCell>{item.reason}</TableCell>
                                        <TableCell>
                                            <FormControlLabel
                                                control={
                                                    <Checkbox
                                                        checked={item.publish}
                                                        onChange={() => handleTogglePublish(index)}
                                                        size="small"
                                                    />
                                                }
                                                label=""
                                            />
                                        </TableCell>
                                    </TableRow>
                                ))}
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
    );
});

export default MlPublishPage;
